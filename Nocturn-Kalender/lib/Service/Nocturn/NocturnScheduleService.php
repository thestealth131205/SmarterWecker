<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Service\Nocturn;

use DateInterval;
use DateTimeImmutable;
use DateTimeZone;
use OCA\NocturnKalender\Db\NocturnJob;
use OCA\NocturnKalender\Db\NocturnJobMapper;
use OCA\NocturnKalender\Db\NocturnJobShare;
use OCA\NocturnKalender\Db\NocturnJobShareMapper;
use OCA\NocturnKalender\Db\NocturnOccurrence;
use OCA\NocturnKalender\Db\NocturnOccurrenceMapper;
use OCA\NocturnKalender\Db\NocturnRotation;
use OCA\NocturnKalender\Db\NocturnRotationDay;
use OCA\NocturnKalender\Db\NocturnRotationDayMapper;
use OCA\NocturnKalender\Db\NocturnRotationMapper;
use OCA\NocturnKalender\Db\NocturnShift;
use OCA\NocturnKalender\Db\NocturnShiftMapper;
use OCA\NocturnKalender\Exception\ClientException;
use OCA\NocturnKalender\Exception\ServiceException;
use OCP\AppFramework\Db\DoesNotExistException;
use OCP\AppFramework\Db\MultipleObjectsReturnedException;
use OCP\AppFramework\Http;
use OCP\DB\Exception as DbException;
use OCP\IDBConnection;
use OCP\IUserManager;
use OCP\Security\ISecureRandom;
use Throwable;
use function array_map;
use function count;
use function max;

/**
 * CRUD + delta-sync for the NocTurn shift/rotation schedule (jobs, shifts,
 * rotation templates and their generated/manual day-by-day occurrences).
 *
 * Every mutable row carries a client-assignable `uid`, `updatedAt` and a
 * soft-delete `deletedAt` tombstone instead of a hard DELETE, so that a
 * bidirectional sync client (the NocTurn Android app) can pull incremental
 * changes via {@see getChangesSince()} and resolve write conflicts with a
 * simple last-write-wins rule based on `updatedAt`.
 */
class NocturnScheduleService {
	public function __construct(
		private NocturnJobMapper $jobMapper,
		private NocturnShiftMapper $shiftMapper,
		private NocturnRotationMapper $rotationMapper,
		private NocturnRotationDayMapper $rotationDayMapper,
		private NocturnOccurrenceMapper $occurrenceMapper,
		private NocturnJobShareMapper $jobShareMapper,
		private IDBConnection $db,
		private ISecureRandom $random,
		private IUserManager $userManager,
	) {
	}

	private function now(): int {
		return (int)round(microtime(true) * 1000);
	}

	private function newUid(): string {
		return $this->random->generate(32, ISecureRandom::CHAR_ALPHANUMERIC);
	}

	/**
	 * @return array{jobs: NocturnJob[], shifts: NocturnShift[], rotations: NocturnRotation[], rotationDays: NocturnRotationDay[], occurrences: NocturnOccurrence[], serverTime: int}
	 * @throws ServiceException
	 */
	public function getChangesSince(string $userId, int $since): array {
		try {
			return [
				'jobs' => $this->jobMapper->findChangedSince($userId, $since),
				'shifts' => $this->shiftMapper->findChangedSince($userId, $since),
				'rotations' => $this->rotationMapper->findChangedSince($userId, $since),
				'rotationDays' => $this->rotationDayMapper->findChangedSince($userId, $since),
				'occurrences' => $this->occurrenceMapper->findChangedSince($userId, $since),
				// Use as the `since` value of the next call.
				'serverTime' => $this->now(),
			];
		} catch (DbException $e) {
			throw new ServiceException('Could not load schedule changes', 0, $e);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function upsertJob(string $userId, ?string $uid, string $name, string $mode, ?int $clientUpdatedAt): NocturnJob {
		$mode = $mode === NocturnJob::MODE_STUDENT ? NocturnJob::MODE_STUDENT : NocturnJob::MODE_SHIFT;

		try {
			$existing = $uid !== null ? $this->findJobOrNull($uid, $userId) : null;

			if ($existing !== null) {
				if ($clientUpdatedAt !== null && $clientUpdatedAt < $existing->getUpdatedAt()) {
					// Stale write - a newer version already lives on the server.
					return $existing;
				}
				$existing->setName($name);
				$existing->setMode($mode);
				$existing->setDeletedAt(null);
				$existing->setUpdatedAt($clientUpdatedAt ?? $this->now());
				return $this->jobMapper->update($existing);
			}

			$job = new NocturnJob();
			$job->setUid($uid ?? $this->newUid());
			$job->setUserId($userId);
			$job->setName($name);
			$job->setMode($mode);
			$now = $this->now();
			$job->setCreatedAt($now);
			$job->setUpdatedAt($clientUpdatedAt ?? $now);
			return $this->jobMapper->insert($job);
		} catch (DbException|MultipleObjectsReturnedException $e) {
			throw new ServiceException('Could not save job', 0, $e);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function deleteJob(string $userId, string $uid): void {
		$this->softDelete($this->jobMapper, $this->findJobOrNull($uid, $userId), 'job');
		$this->jobShareMapper->deleteAllForJob($userId, $uid);
	}

	/**
	 * Grants (or updates) another Nextcloud user read or read/write access
	 * to one job's full schedule (shifts, rotations, generated/manual
	 * occurrences) - the NocTurn equivalent of "share this calendar with...".
	 *
	 * @throws ClientException if the job doesn't exist, the target user
	 *                          doesn't exist, or the target is the owner
	 * @throws ServiceException
	 */
	public function shareJob(string $ownerId, string $jobUid, string $sharedWithUserId, string $permission): NocturnJobShare {
		if ($this->findJobOrNull($jobUid, $ownerId) === null) {
			throw new ClientException('Job not found', 0, null, Http::STATUS_NOT_FOUND);
		}
		if ($sharedWithUserId === $ownerId) {
			throw new ClientException('Cannot share a job with yourself', 0, null, Http::STATUS_UNPROCESSABLE_ENTITY);
		}
		if (!$this->userManager->userExists($sharedWithUserId)) {
			throw new ClientException('User not found', 0, null, Http::STATUS_NOT_FOUND);
		}
		$permission = $permission === NocturnJobShare::PERMISSION_WRITE
			? NocturnJobShare::PERMISSION_WRITE
			: NocturnJobShare::PERMISSION_READ;

		try {
			$now = $this->now();
			try {
				$share = $this->jobShareMapper->findOne($ownerId, $jobUid, $sharedWithUserId);
				$share->setPermission($permission);
				$share->setUpdatedAt($now);
				return $this->jobShareMapper->update($share);
			} catch (DoesNotExistException) {
				$share = new NocturnJobShare();
				$share->setJobUid($jobUid);
				$share->setOwnerId($ownerId);
				$share->setSharedWith($sharedWithUserId);
				$share->setPermission($permission);
				$share->setCreatedAt($now);
				$share->setUpdatedAt($now);
				return $this->jobShareMapper->insert($share);
			}
		} catch (DbException|MultipleObjectsReturnedException $e) {
			throw new ServiceException('Could not share job', 0, $e);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function unshareJob(string $ownerId, string $jobUid, string $sharedWithUserId): void {
		try {
			$this->jobShareMapper->deleteOne($ownerId, $jobUid, $sharedWithUserId);
		} catch (DbException $e) {
			throw new ServiceException('Could not unshare job', 0, $e);
		}
	}

	/**
	 * @return NocturnJobShare[]
	 * @throws ServiceException
	 */
	public function listSharesForJob(string $ownerId, string $jobUid): array {
		try {
			return $this->jobShareMapper->findForJob($ownerId, $jobUid);
		} catch (DbException $e) {
			throw new ServiceException('Could not load job shares', 0, $e);
		}
	}

	/**
	 * Everything shared with the given user by other owners: for each
	 * share, the owner's job plus its shifts and rotations. Occurrences are
	 * loaded separately per date range via {@see getSharedJobOccurrences()},
	 * same as NocTurn's own calendar only ever loads the visible month.
	 *
	 * @return array<int, array{job: NocturnJob, ownerId: string, permission: string, shifts: NocturnShift[], rotations: NocturnRotation[]}>
	 * @throws ServiceException
	 */
	public function getSharedWithMe(string $userId): array {
		try {
			$shares = $this->jobShareMapper->findSharedWithUser($userId);
		} catch (DbException $e) {
			throw new ServiceException('Could not load shared jobs', 0, $e);
		}

		$result = [];
		foreach ($shares as $share) {
			$job = $this->findJobOrNull($share->getJobUid(), $share->getOwnerId());
			if ($job === null) {
				// Owner deleted the job (or it never existed) - a stale
				// share row, nothing meaningful to show for it.
				continue;
			}
			try {
				$result[] = [
					'job' => $job,
					'ownerId' => $share->getOwnerId(),
					'permission' => $share->getPermission(),
					'shifts' => $this->shiftMapper->findByJobForUser($job->getUid(), $share->getOwnerId()),
					'rotations' => $this->rotationMapper->findByJobForUser($job->getUid(), $share->getOwnerId()),
				];
			} catch (DbException $e) {
				throw new ServiceException('Could not load shared job details', 0, $e);
			}
		}
		return $result;
	}

	/**
	 * Occurrences of a job shared with the caller, within a date range -
	 * mirrors {@see NocturnOccurrenceMapper::findByJobAndRangeForUser()} but
	 * authorized via the share grant instead of ownership.
	 *
	 * @return NocturnOccurrence[]
	 * @throws ClientException if nothing is shared with the caller for this job
	 * @throws ServiceException
	 */
	public function getSharedJobOccurrences(string $userId, string $ownerId, string $jobUid, int $fromEpochDay, int $toEpochDayExclusive): array {
		$this->requireShareAccess($userId, $ownerId, $jobUid);
		try {
			return $this->occurrenceMapper->findByJobAndRangeForUser($jobUid, $ownerId, $fromEpochDay, $toEpochDayExclusive);
		} catch (DbException $e) {
			throw new ServiceException('Could not load shared occurrences', 0, $e);
		}
	}

	/**
	 * Lets a WRITE-permission collaborator enter/edit a single day's shift
	 * on someone else's job - e.g. agreeing to a shift swap - without
	 * giving them any access beyond that one job's occurrences.
	 *
	 * @throws ClientException if the caller only has READ access (or no
	 *                          share at all)
	 * @throws ServiceException
	 */
	public function upsertSharedOccurrence(string $userId, string $ownerId, string $jobUid, ?string $uid, int $epochDay, ?string $shiftUid, ?int $clientUpdatedAt): NocturnOccurrence {
		$this->requireShareAccess($userId, $ownerId, $jobUid, NocturnJobShare::PERMISSION_WRITE);
		return $this->upsertManualOccurrence($ownerId, $uid, $jobUid, $epochDay, $shiftUid, $clientUpdatedAt);
	}

	/**
	 * @throws ClientException if the caller only has READ access (or no
	 *                          share at all)
	 * @throws ServiceException
	 */
	public function deleteSharedOccurrence(string $userId, string $ownerId, string $jobUid, string $uid): void {
		$this->requireShareAccess($userId, $ownerId, $jobUid, NocturnJobShare::PERMISSION_WRITE);
		$occurrence = $this->findOccurrenceOrNull($uid, $ownerId);
		if ($occurrence !== null && $occurrence->getJobUid() !== $jobUid) {
			throw new ClientException('Day entry does not belong to this job', 0, null, Http::STATUS_FORBIDDEN);
		}
		$this->softDelete($this->occurrenceMapper, $occurrence, 'day entry');
	}

	/**
	 * @throws ClientException if no share (of at least the required
	 *                          permission) exists for this user/job
	 * @throws ServiceException
	 */
	private function requireShareAccess(string $userId, string $ownerId, string $jobUid, string $minPermission = NocturnJobShare::PERMISSION_READ): void {
		try {
			$share = $this->jobShareMapper->findOne($ownerId, $jobUid, $userId);
		} catch (DoesNotExistException) {
			throw new ClientException('This job is not shared with you', 0, null, Http::STATUS_FORBIDDEN);
		} catch (MultipleObjectsReturnedException $e) {
			throw new ServiceException('Ambiguous job share', 0, $e);
		}
		if ($minPermission === NocturnJobShare::PERMISSION_WRITE && $share->getPermission() !== NocturnJobShare::PERMISSION_WRITE) {
			throw new ClientException('You only have read access to this job', 0, null, Http::STATUS_FORBIDDEN);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function upsertShift(
		string $userId,
		?string $uid,
		string $jobUid,
		string $name,
		string $color,
		int $startHour,
		int $startMinute,
		int $durationMinutes,
		int $earliestWakeOffsetMinutes,
		int $latestWakeupOffsetMinutes,
		?int $clientUpdatedAt,
	): NocturnShift {
		try {
			$existing = $uid !== null ? $this->findShiftOrNull($uid, $userId) : null;

			if ($existing !== null) {
				if ($clientUpdatedAt !== null && $clientUpdatedAt < $existing->getUpdatedAt()) {
					return $existing;
				}
				$existing->setJobUid($jobUid);
				$existing->setName($name);
				$existing->setColor($color);
				$existing->setStartHour($startHour);
				$existing->setStartMinute($startMinute);
				$existing->setDurationMinutes($durationMinutes);
				$existing->setEarliestWakeOffsetMinutes($earliestWakeOffsetMinutes);
				$existing->setLatestWakeupOffsetMinutes($latestWakeupOffsetMinutes);
				$existing->setDeletedAt(null);
				$existing->setUpdatedAt($clientUpdatedAt ?? $this->now());
				return $this->shiftMapper->update($existing);
			}

			$shift = new NocturnShift();
			$shift->setUid($uid ?? $this->newUid());
			$shift->setUserId($userId);
			$shift->setJobUid($jobUid);
			$shift->setName($name);
			$shift->setColor($color);
			$shift->setStartHour($startHour);
			$shift->setStartMinute($startMinute);
			$shift->setDurationMinutes($durationMinutes);
			$shift->setEarliestWakeOffsetMinutes($earliestWakeOffsetMinutes);
			$shift->setLatestWakeupOffsetMinutes($latestWakeupOffsetMinutes);
			$now = $this->now();
			$shift->setCreatedAt($now);
			$shift->setUpdatedAt($clientUpdatedAt ?? $now);
			return $this->shiftMapper->insert($shift);
		} catch (DbException|MultipleObjectsReturnedException $e) {
			throw new ServiceException('Could not save shift', 0, $e);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function deleteShift(string $userId, string $uid): void {
		$this->softDelete($this->shiftMapper, $this->findShiftOrNull($uid, $userId), 'shift');
	}

	/**
	 * @param array<int, string|null> $dayShiftUids ordered pattern, one entry per day of the rotation; null = free day
	 * @throws ServiceException
	 */
	public function upsertRotation(string $userId, ?string $uid, string $jobUid, string $name, array $dayShiftUids, ?int $clientUpdatedAt): NocturnRotation {
		try {
			$this->db->beginTransaction();

			$existing = $uid !== null ? $this->findRotationOrNull($uid, $userId) : null;

			if ($existing !== null && $clientUpdatedAt !== null && $clientUpdatedAt < $existing->getUpdatedAt()) {
				// Stale write - keep the newer server version and its pattern untouched.
				$this->db->commit();
				return $existing;
			}

			if ($existing !== null) {
				$existing->setJobUid($jobUid);
				$existing->setName($name);
				$existing->setDeletedAt(null);
				$existing->setUpdatedAt($clientUpdatedAt ?? $this->now());
				$rotation = $this->rotationMapper->update($existing);
			} else {
				$rotation = new NocturnRotation();
				$rotation->setUid($uid ?? $this->newUid());
				$rotation->setUserId($userId);
				$rotation->setJobUid($jobUid);
				$rotation->setName($name);
				$now = $this->now();
				$rotation->setCreatedAt($now);
				$rotation->setUpdatedAt($clientUpdatedAt ?? $now);
				$rotation = $this->rotationMapper->insert($rotation);
			}

			// The pattern is always saved as a whole: wipe the previous days
			// and re-insert the new ones, same as NocTurn's own rotation
			// editor treats the pattern as a template, not individually
			// synced rows.
			$this->rotationDayMapper->deleteAllForRotation($rotation->getUid(), $userId);
			$now = $this->now();
			foreach ($dayShiftUids as $index => $shiftUid) {
				$day = new NocturnRotationDay();
				$day->setUid($this->newUid());
				$day->setUserId($userId);
				$day->setRotationUid($rotation->getUid());
				$day->setDayIndex((int)$index);
				$day->setShiftUid($shiftUid !== null && $shiftUid !== '' ? $shiftUid : null);
				$day->setCreatedAt($now);
				$day->setUpdatedAt($now);
				$this->rotationDayMapper->insert($day);
			}

			$this->db->commit();
			return $rotation;
		} catch (Throwable $e) {
			$this->db->rollBack();
			if ($e instanceof ServiceException) {
				throw $e;
			}
			throw new ServiceException('Could not save rotation', 0, $e);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function deleteRotation(string $userId, string $uid): void {
		$this->softDelete($this->rotationMapper, $this->findRotationOrNull($uid, $userId), 'rotation');
	}

	/**
	 * Applies a rotation's day-for-day pattern to a concrete date range,
	 * generating/replacing the occurrence rows the calendar actually
	 * displays - mirrors NocTurn's own `CalendarRepository.applyRotationRange`.
	 *
	 * @return array{fromEpochDay: int, toEpochDayExclusive: int, generatedDays: int}
	 * @throws ClientException if the rotation doesn't exist or has an empty pattern
	 * @throws ServiceException
	 */
	public function applyRotation(string $userId, string $rotationUid, int $startEpochDay, int $months): array {
		try {
			$rotation = $this->rotationMapper->findByUidForUser($rotationUid, $userId);
		} catch (DoesNotExistException|MultipleObjectsReturnedException $e) {
			throw new ClientException('Rotation not found', 0, $e, Http::STATUS_NOT_FOUND);
		}

		try {
			$days = $this->rotationDayMapper->findByRotationForUser($rotationUid, $userId);
		} catch (DbException $e) {
			throw new ServiceException('Could not load rotation pattern', 0, $e);
		}

		$length = count($days);
		if ($length === 0) {
			throw new ClientException('Rotation has an empty pattern', 0, null, Http::STATUS_UNPROCESSABLE_ENTITY);
		}

		$shiftUidsByIndex = array_map(static fn (NocturnRotationDay $day): ?string => $day->getShiftUid(), $days);

		$toEpochDayExclusive = $this->addMonthsToEpochDay($startEpochDay, $months);
		if ($toEpochDayExclusive <= $startEpochDay) {
			throw new ClientException('Invalid date range', 0, null, Http::STATUS_UNPROCESSABLE_ENTITY);
		}

		try {
			$this->db->beginTransaction();

			$this->occurrenceMapper->deleteRotationOccurrencesInRange($rotationUid, $userId, $startEpochDay, $toEpochDayExclusive);

			$now = $this->now();
			$jobUid = $rotation->getJobUid();
			for ($epochDay = $startEpochDay; $epochDay < $toEpochDayExclusive; $epochDay++) {
				$index = ($epochDay - $startEpochDay) % $length;
				$occurrence = new NocturnOccurrence();
				$occurrence->setUid($this->newUid());
				$occurrence->setUserId($userId);
				$occurrence->setJobUid($jobUid);
				$occurrence->setEpochDay($epochDay);
				$occurrence->setShiftUid($shiftUidsByIndex[$index] ?? null);
				$occurrence->setRotationUid($rotationUid);
				$occurrence->setSource(NocturnOccurrence::SOURCE_ROTATION);
				$occurrence->setCreatedAt($now);
				$occurrence->setUpdatedAt($now);
				$this->occurrenceMapper->insert($occurrence);
			}

			$this->db->commit();
		} catch (Throwable $e) {
			$this->db->rollBack();
			throw new ServiceException('Could not apply rotation', 0, $e);
		}

		return [
			'fromEpochDay' => $startEpochDay,
			'toEpochDayExclusive' => $toEpochDayExclusive,
			'generatedDays' => $toEpochDayExclusive - $startEpochDay,
		];
	}

	private function addMonthsToEpochDay(int $epochDay, int $months): int {
		$months = max(1, $months);
		$start = (new DateTimeImmutable('@0'))
			->setTimezone(new DateTimeZone('UTC'))
			->modify(sprintf('+%d days', $epochDay));
		$end = $start->add(new DateInterval('P' . $months . 'M'));
		$diffDays = (int)floor(($end->getTimestamp() - $start->getTimestamp()) / 86400);
		return $epochDay + $diffDays;
	}

	/**
	 * Manually enters (or edits) a single day's shift, independent of any
	 * rotation - e.g. a one-off swap or a free day override.
	 *
	 * @throws ServiceException
	 */
	public function upsertManualOccurrence(string $userId, ?string $uid, string $jobUid, int $epochDay, ?string $shiftUid, ?int $clientUpdatedAt): NocturnOccurrence {
		try {
			$existing = $uid !== null ? $this->findOccurrenceOrNull($uid, $userId) : null;

			if ($existing !== null) {
				if ($clientUpdatedAt !== null && $clientUpdatedAt < $existing->getUpdatedAt()) {
					return $existing;
				}
				$existing->setJobUid($jobUid);
				$existing->setEpochDay($epochDay);
				$existing->setShiftUid($shiftUid);
				$existing->setDeletedAt(null);
				$existing->setUpdatedAt($clientUpdatedAt ?? $this->now());
				return $this->occurrenceMapper->update($existing);
			}

			$occurrence = new NocturnOccurrence();
			$occurrence->setUid($uid ?? $this->newUid());
			$occurrence->setUserId($userId);
			$occurrence->setJobUid($jobUid);
			$occurrence->setEpochDay($epochDay);
			$occurrence->setShiftUid($shiftUid);
			$occurrence->setRotationUid(null);
			$occurrence->setSource(NocturnOccurrence::SOURCE_MANUAL);
			$now = $this->now();
			$occurrence->setCreatedAt($now);
			$occurrence->setUpdatedAt($clientUpdatedAt ?? $now);
			return $this->occurrenceMapper->insert($occurrence);
		} catch (DbException|MultipleObjectsReturnedException $e) {
			throw new ServiceException('Could not save day entry', 0, $e);
		}
	}

	/**
	 * @throws ServiceException
	 */
	public function deleteOccurrence(string $userId, string $uid): void {
		$this->softDelete($this->occurrenceMapper, $this->findOccurrenceOrNull($uid, $userId), 'day entry');
	}

	/**
	 * @template T of NocturnJob|NocturnShift|NocturnRotation|NocturnOccurrence
	 * @param NocturnJobMapper|NocturnShiftMapper|NocturnRotationMapper|NocturnOccurrenceMapper $mapper
	 * @param T|null $entity
	 * @throws ServiceException
	 */
	private function softDelete($mapper, $entity, string $label): void {
		if ($entity === null) {
			// Deleting something that's already gone (or never existed on
			// this server) is a no-op for an idempotent sync delete call.
			return;
		}
		try {
			$now = $this->now();
			$entity->setDeletedAt($now);
			$entity->setUpdatedAt($now);
			$mapper->update($entity);
		} catch (DbException $e) {
			throw new ServiceException('Could not delete ' . $label, 0, $e);
		}
	}

	private function findJobOrNull(string $uid, string $userId): ?NocturnJob {
		try {
			return $this->jobMapper->findByUidForUser($uid, $userId);
		} catch (DoesNotExistException) {
			return null;
		} catch (MultipleObjectsReturnedException $e) {
			throw new ServiceException('Ambiguous job uid', 0, $e);
		}
	}

	private function findShiftOrNull(string $uid, string $userId): ?NocturnShift {
		try {
			return $this->shiftMapper->findByUidForUser($uid, $userId);
		} catch (DoesNotExistException) {
			return null;
		} catch (MultipleObjectsReturnedException $e) {
			throw new ServiceException('Ambiguous shift uid', 0, $e);
		}
	}

	private function findRotationOrNull(string $uid, string $userId): ?NocturnRotation {
		try {
			return $this->rotationMapper->findByUidForUser($uid, $userId);
		} catch (DoesNotExistException) {
			return null;
		} catch (MultipleObjectsReturnedException $e) {
			throw new ServiceException('Ambiguous rotation uid', 0, $e);
		}
	}

	private function findOccurrenceOrNull(string $uid, string $userId): ?NocturnOccurrence {
		try {
			return $this->occurrenceMapper->findByUidForUser($uid, $userId);
		} catch (DoesNotExistException) {
			return null;
		} catch (MultipleObjectsReturnedException $e) {
			throw new ServiceException('Ambiguous day entry uid', 0, $e);
		}
	}
}
