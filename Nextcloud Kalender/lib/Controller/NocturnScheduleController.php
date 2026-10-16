<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\Calendar\Controller;

use OCA\Calendar\AppInfo\Application;
use OCA\Calendar\Exception\ClientException;
use OCA\Calendar\Exception\ServiceException;
use OCA\Calendar\Http\JsonResponse;
use OCA\Calendar\Service\Nocturn\NocturnScheduleService;
use OCP\AppFramework\Controller;
use OCP\AppFramework\Http;
use OCP\IRequest;
use Psr\Log\LoggerInterface;

/**
 * REST API for the NocTurn shift/rotation schedule (jobs, shifts, rotation
 * templates, generated/manual day entries) and its incremental sync feed.
 * Used by the NocTurn Android app and, in the future, by this app's own
 * schedule management UI - both talk to the same per-user data.
 */
class NocturnScheduleController extends Controller {

	public function __construct(
		IRequest $request,
		private NocturnScheduleService $service,
		private LoggerInterface $logger,
		private ?string $userId,
	) {
		parent::__construct(Application::APP_ID, $request);
	}

	private function requireUser(): ?JsonResponse {
		if ($this->userId === null) {
			$this->logger->warning('No user found for NocTurn schedule request');
			return JsonResponse::fail(null, Http::STATUS_UNAUTHORIZED);
		}
		return null;
	}

	/**
	 * @NoAdminRequired
	 *
	 * Incremental sync: returns every job/shift/rotation/rotation day/
	 * occurrence that changed (including soft-deleted tombstones) since the
	 * given server timestamp (milliseconds). Pass 0 for a full initial sync.
	 * The response's `data.serverTime` is the value to pass as `since` on
	 * the next call.
	 */
	public function sync(int $since = 0): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$changes = $this->service->getChangesSince($this->userId, $since);
			return JsonResponse::success($changes);
		} catch (ServiceException $e) {
			$this->logger->error('Could not load NocTurn schedule changes', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function upsertJob(?string $uid, string $name, string $mode = 'SHIFT', ?int $updatedAt = null): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$job = $this->service->upsertJob($this->userId, $uid, $name, $mode, $updatedAt);
			return JsonResponse::success($job);
		} catch (ServiceException $e) {
			$this->logger->error('Could not save NocTurn job', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function deleteJob(string $uid): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$this->service->deleteJob($this->userId, $uid);
			return JsonResponse::success();
		} catch (ServiceException $e) {
			$this->logger->error('Could not delete NocTurn job', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function upsertShift(
		?string $uid,
		string $jobUid,
		string $name,
		string $color,
		int $startHour,
		int $startMinute,
		int $durationMinutes,
		int $earliestWakeOffsetMinutes = 0,
		int $latestWakeupOffsetMinutes = 0,
		?int $updatedAt = null,
	): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$shift = $this->service->upsertShift(
				$this->userId,
				$uid,
				$jobUid,
				$name,
				$color,
				$startHour,
				$startMinute,
				$durationMinutes,
				$earliestWakeOffsetMinutes,
				$latestWakeupOffsetMinutes,
				$updatedAt,
			);
			return JsonResponse::success($shift);
		} catch (ServiceException $e) {
			$this->logger->error('Could not save NocTurn shift', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function deleteShift(string $uid): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$this->service->deleteShift($this->userId, $uid);
			return JsonResponse::success();
		} catch (ServiceException $e) {
			$this->logger->error('Could not delete NocTurn shift', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * @param string[]|null[] $days ordered rotation pattern, one entry per day; null/empty = free day
	 */
	public function upsertRotation(?string $uid, string $jobUid, string $name, array $days, ?int $updatedAt = null): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$rotation = $this->service->upsertRotation($this->userId, $uid, $jobUid, $name, $days, $updatedAt);
			return JsonResponse::success($rotation);
		} catch (ServiceException $e) {
			$this->logger->error('Could not save NocTurn rotation', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function deleteRotation(string $uid): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$this->service->deleteRotation($this->userId, $uid);
			return JsonResponse::success();
		} catch (ServiceException $e) {
			$this->logger->error('Could not delete NocTurn rotation', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * Generates (or re-generates) the calendar occurrences for a rotation
	 * template over [startEpochDay, startEpochDay + months), repeating its
	 * pattern. `startEpochDay` is days since 1970-01-01 UTC (matches Java's
	 * `LocalDate.toEpochDay()`, used by the NocTurn Android app).
	 */
	public function applyRotation(string $uid, int $startEpochDay, int $months): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$result = $this->service->applyRotation($this->userId, $uid, $startEpochDay, $months);
			return JsonResponse::success($result);
		} catch (ClientException $e) {
			$this->logger->warning('Could not apply NocTurn rotation', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), $e->getHttpCode() ?? Http::STATUS_BAD_REQUEST);
		} catch (ServiceException $e) {
			$this->logger->error('Could not apply NocTurn rotation', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * Creates or edits a single day's shift, independent of any rotation.
	 */
	public function upsertOccurrence(?string $uid, string $jobUid, int $epochDay, ?string $shiftUid = null, ?int $updatedAt = null): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$occurrence = $this->service->upsertManualOccurrence($this->userId, $uid, $jobUid, $epochDay, $shiftUid, $updatedAt);
			return JsonResponse::success($occurrence);
		} catch (ServiceException $e) {
			$this->logger->error('Could not save NocTurn day entry', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function deleteOccurrence(string $uid): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$this->service->deleteOccurrence($this->userId, $uid);
			return JsonResponse::success();
		} catch (ServiceException $e) {
			$this->logger->error('Could not delete NocTurn day entry', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * Grants (or updates) another Nextcloud user read or read/write access
	 * to one job's full schedule - the NocTurn equivalent of sharing a
	 * calendar.
	 */
	public function shareJob(string $uid, string $sharedWithUserId, string $permission = 'READ'): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$share = $this->service->shareJob($this->userId, $uid, $sharedWithUserId, $permission);
			return JsonResponse::success($share);
		} catch (ClientException $e) {
			$this->logger->warning('Could not share NocTurn job', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), $e->getHttpCode() ?? Http::STATUS_BAD_REQUEST);
		} catch (ServiceException $e) {
			$this->logger->error('Could not share NocTurn job', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function unshareJob(string $uid, string $sharedWithUserId): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$this->service->unshareJob($this->userId, $uid, $sharedWithUserId);
			return JsonResponse::success();
		} catch (ServiceException $e) {
			$this->logger->error('Could not unshare NocTurn job', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function listJobShares(string $uid): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			return JsonResponse::success($this->service->listSharesForJob($this->userId, $uid));
		} catch (ServiceException $e) {
			$this->logger->error('Could not load NocTurn job shares', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * Every job (plus its shifts/rotations) another Nextcloud user has
	 * shared with the caller.
	 */
	public function sharedWithMe(): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			return JsonResponse::success($this->service->getSharedWithMe($this->userId));
		} catch (ServiceException $e) {
			$this->logger->error('Could not load jobs shared with user', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * Occurrences of a job shared with the caller, within a date range.
	 */
	public function sharedOccurrences(string $ownerId, string $jobUid, int $fromEpochDay, int $toEpochDayExclusive): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$occurrences = $this->service->getSharedJobOccurrences($this->userId, $ownerId, $jobUid, $fromEpochDay, $toEpochDayExclusive);
			return JsonResponse::success($occurrences);
		} catch (ClientException $e) {
			$this->logger->warning('Could not load shared NocTurn occurrences', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), $e->getHttpCode() ?? Http::STATUS_BAD_REQUEST);
		} catch (ServiceException $e) {
			$this->logger->error('Could not load shared NocTurn occurrences', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 *
	 * Lets a WRITE-permission collaborator enter/edit a single day's shift
	 * on someone else's shared job.
	 */
	public function upsertSharedOccurrence(string $ownerId, string $jobUid, ?string $uid, int $epochDay, ?string $shiftUid = null, ?int $updatedAt = null): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$occurrence = $this->service->upsertSharedOccurrence($this->userId, $ownerId, $jobUid, $uid, $epochDay, $shiftUid, $updatedAt);
			return JsonResponse::success($occurrence);
		} catch (ClientException $e) {
			$this->logger->warning('Could not save shared NocTurn day entry', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), $e->getHttpCode() ?? Http::STATUS_BAD_REQUEST);
		} catch (ServiceException $e) {
			$this->logger->error('Could not save shared NocTurn day entry', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * @NoAdminRequired
	 */
	public function deleteSharedOccurrence(string $ownerId, string $jobUid, string $uid): JsonResponse {
		if (($response = $this->requireUser()) !== null) {
			return $response;
		}

		try {
			$this->service->deleteSharedOccurrence($this->userId, $ownerId, $jobUid, $uid);
			return JsonResponse::success();
		} catch (ClientException $e) {
			$this->logger->warning('Could not delete shared NocTurn day entry', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), $e->getHttpCode() ?? Http::STATUS_BAD_REQUEST);
		} catch (ServiceException $e) {
			$this->logger->error('Could not delete shared NocTurn day entry', ['exception' => $e]);
			return JsonResponse::fail($e->getMessage(), Http::STATUS_INTERNAL_SERVER_ERROR);
		}
	}
}
