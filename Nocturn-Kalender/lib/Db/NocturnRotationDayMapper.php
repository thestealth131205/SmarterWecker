<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Db;

use OCP\AppFramework\Db\QBMapper;
use OCP\DB\QueryBuilder\IQueryBuilder;
use OCP\IDBConnection;

/**
 * @template-extends QBMapper<NocturnRotationDay>
 */
class NocturnRotationDayMapper extends QBMapper {
	public function __construct(IDBConnection $db) {
		parent::__construct($db, 'calendar_noct_rot_days');
	}

	/**
	 * @return NocturnRotationDay[] ordered by their position in the pattern
	 */
	public function findByRotationForUser(string $rotationUid, string $userId): array {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('rotation_uid', $qb->createNamedParameter($rotationUid)))
			->andWhere($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)))
			->andWhere($qb->expr()->isNull('deleted_at'))
			->orderBy('day_index', 'ASC');
		return $this->findEntities($qb);
	}

	/**
	 * @return NocturnRotationDay[]
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
	 * Hard-deletes all (non-tombstone, still-alive) days of a rotation so a
	 * fresh pattern can be inserted in their place. Rotation days are only
	 * ever replaced wholesale together with their parent rotation, so unlike
	 * jobs/shifts/rotations/occurrences they don't need individual
	 * tombstones for sync - the parent rotation's own updatedAt is enough to
	 * signal "pattern changed, re-fetch all days".
	 */
	public function deleteAllForRotation(string $rotationUid, string $userId): void {
		$qb = $this->db->getQueryBuilder();
		$qb->delete($this->getTableName())
			->where($qb->expr()->eq('rotation_uid', $qb->createNamedParameter($rotationUid)))
			->andWhere($qb->expr()->eq('user_id', $qb->createNamedParameter($userId)));
		$qb->executeStatement();
	}
}
