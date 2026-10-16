<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Db;

use JsonSerializable;
use OCP\AppFramework\Db\Entity;
use OCP\DB\Types;
use ReturnTypeWillChange;

/**
 * Grants one other Nextcloud user read (or read/write) access to a NocTurn
 * job's full schedule - its shifts, rotations and generated/manual
 * occurrences - without duplicating any of that data into the grantee's own
 * rows. The grantee's client resolves the shared schedule by reading the
 * owner's rows directly, authorized via this grant.
 *
 * @method int getId()
 * @method void setId(int $id)
 * @method string getJobUid()
 * @method void setJobUid(string $jobUid)
 * @method string getOwnerId()
 * @method void setOwnerId(string $ownerId)
 * @method string getSharedWith()
 * @method void setSharedWith(string $sharedWith)
 * @method string getPermission()
 * @method void setPermission(string $permission)
 * @method int getCreatedAt()
 * @method void setCreatedAt(int $createdAt)
 * @method int getUpdatedAt()
 * @method void setUpdatedAt(int $updatedAt)
 */
class NocturnJobShare extends Entity implements JsonSerializable {
	public const PERMISSION_READ = 'READ';
	public const PERMISSION_WRITE = 'WRITE';

	/** @var string */
	protected $jobUid;

	/** @var string */
	protected $ownerId;

	/** @var string */
	protected $sharedWith;

	/** @var string */
	protected $permission = self::PERMISSION_READ;

	/** @var int */
	protected $createdAt;

	/** @var int */
	protected $updatedAt;

	public function __construct() {
		$this->addType('createdAt', Types::BIGINT);
		$this->addType('updatedAt', Types::BIGINT);
	}

	#[\Override]
	#[ReturnTypeWillChange]
	public function jsonSerialize() {
		return [
			'jobUid' => $this->getJobUid(),
			'ownerId' => $this->getOwnerId(),
			'sharedWith' => $this->getSharedWith(),
			'permission' => $this->getPermission(),
			'createdAt' => $this->getCreatedAt(),
			'updatedAt' => $this->getUpdatedAt(),
		];
	}
}
