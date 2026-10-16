<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Db;

use OCP\AppFramework\Db\DoesNotExistException;
use OCP\AppFramework\Db\QBMapper;
use OCP\IDBConnection;

/**
 * @template-extends QBMapper<NocturnJobShare>
 */
class NocturnJobShareMapper extends QBMapper {
	public function __construct(IDBConnection $db) {
		parent::__construct($db, 'calendar_noct_job_shares');
	}

	/**
	 * @return NocturnJobShare[]
	 */
	public function findForJob(string $ownerId, string $jobUid): array {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('owner_id', $qb->createNamedParameter($ownerId)))
			->andWhere($qb->expr()->eq('job_uid', $qb->createNamedParameter($jobUid)));
		return $this->findEntities($qb);
	}

	/**
	 * @return NocturnJobShare[]
	 */
	public function findSharedWithUser(string $userId): array {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('shared_with', $qb->createNamedParameter($userId)));
		return $this->findEntities($qb);
	}

	/**
	 * @throws DoesNotExistException
	 */
	public function findOne(string $ownerId, string $jobUid, string $sharedWith): NocturnJobShare {
		$qb = $this->db->getQueryBuilder();
		$qb->select('*')
			->from($this->getTableName())
			->where($qb->expr()->eq('owner_id', $qb->createNamedParameter($ownerId)))
			->andWhere($qb->expr()->eq('job_uid', $qb->createNamedParameter($jobUid)))
			->andWhere($qb->expr()->eq('shared_with', $qb->createNamedParameter($sharedWith)));
		return $this->findEntity($qb);
	}

	public function deleteOne(string $ownerId, string $jobUid, string $sharedWith): void {
		$qb = $this->db->getQueryBuilder();
		$qb->delete($this->getTableName())
			->where($qb->expr()->eq('owner_id', $qb->createNamedParameter($ownerId)))
			->andWhere($qb->expr()->eq('job_uid', $qb->createNamedParameter($jobUid)))
			->andWhere($qb->expr()->eq('shared_with', $qb->createNamedParameter($sharedWith)));
		$qb->executeStatement();
	}

	/**
	 * Removes every share of a job at once, e.g. when the job itself gets
	 * deleted - stale shares would otherwise keep pointing at a job that no
	 * longer resolves to anything.
	 */
	public function deleteAllForJob(string $ownerId, string $jobUid): void {
		$qb = $this->db->getQueryBuilder();
		$qb->delete($this->getTableName())
			->where($qb->expr()->eq('owner_id', $qb->createNamedParameter($ownerId)))
			->andWhere($qb->expr()->eq('job_uid', $qb->createNamedParameter($jobUid)));
		$qb->executeStatement();
	}
}
