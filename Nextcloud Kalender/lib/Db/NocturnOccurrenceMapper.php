<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\Calendar\Db;

use OCP\AppFramework\Db\DoesNotExistException;
use OCP\AppFramework\Db\QBMapper;
use OCP\DB\QueryBuilder\IQueryBuilder;
use OCP\IDBConnection;

/**
 * @template-extends QBMapper<NocturnOccurrence>
 */
class NocturnOccurrenceMapper extends QBMapper {
	public function __construct(IDBConnection $db) {
		parent::__construct($db, 'calendar_noct_occurr');
	}

	/**
	 * @return NocturnOccurrence[]
	 */
	public function findByJobAndRangeForUser(string $jobUid, string $userId, int $fromEpochDay, int $toEpochDayExclusive): array {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('job_uid', $qb->createNamedParameter($jobUid)))
			->andWhere($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
			->andWhere($qb->expr()->isNull('deleted_at'))
			->andWhere($qb->expr()->gte('epoch_day', $qb->createNamedParameter($fromEpochDay, IQueryBuilder::PARAM_INT)))
			->andWhere($qb->expr()->lt('epoch_day', $qb->createNamedParameter($toEpochDayExclusive, IQueryBuilder::PARAM_INT)))
			->orderBy('epoch_day', 'ASC');
		return $this->findEntities($qb);
	}

	/**
	 * @return NocturnOccurrence[]
	 */
	public function findChangedSince(string $userId, int $since): array {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
			->andWhere($qb->expr()->gt('updated_at', $qb->createNamedParameter($since, IQueryBuilder::PARAM_INT)));
		return $this->findEntities($qb);
	}

	/**
	 * @throws DoesNotExistException
	 */
	public function findByUidForUser(string $uid, string $userId): NocturnOccurrence {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('uid', $qb->createNamedParameter($uid)))
			->andWhere($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)));
		return $this->findEntity($qb);
	}

	/**
	 * Removes all still-alive, rotation-generated occurrences of one
	 * specific rotation within [fromEpochDay, toEpochDayExclusive), so the
	 * range can be re-filled with a freshly generated pattern. Manual
	 * occurrences are never touched by this.
	 */
	public function deleteRotationOccurrencesInRange(string $rotationUid, string $userId, int $fromEpochDay, int $toEpochDayExclusive): void {
		$qb = $this->db->getQueryBuilder();
		$qb->delete($this->getTableName())
			->where($qb->expr()->eq('rotation_uid', $qb->createNamedParameter($rotationUid)))
			->andWhere($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
			->andWhere($qb->expr()->eq('source', $qb->createNamedParameter(NocturnOccurrence::SOURCE_ROTATION)))
			->andWhere($qb->expr()->gte('epoch_day', $qb->createNamedParameter($fromEpochDay, IQueryBuilder::PARAM_INT)))
			->andWhere($qb->expr()->lt('epoch_day', $qb->createNamedParameter($toEpochDayExclusive, IQueryBuilder::PARAM_INT)));
		$qb->executeStatement();
	}
}
