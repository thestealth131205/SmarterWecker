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
 * A reusable rotation template (day-for-day pattern). Has no start date of
 * its own - it is only a pattern that gets applied to a concrete date range
 * via {@see NocturnOccurrenceMapper}.
 *
 * @method string getUid()
 * @method void setUid(string $uid)
 * @method string getUserId()
 * @method void setUserId(string $userId)
 * @method string getJobUid()
 * @method void setJobUid(string $jobUid)
 * @method string getName()
 * @method void setName(string $name)
 * @method int getCreatedAt()
 * @method void setCreatedAt(int $createdAt)
 * @method int getUpdatedAt()
 * @method void setUpdatedAt(int $updatedAt)
 * @method int|null getDeletedAt()
 * @method void setDeletedAt(?int $deletedAt)
 */
class NocturnRotation extends Entity implements JsonSerializable {
	/** @var string */
	protected $uid;

	/** @var string */
	protected $userId;

	/** @var string */
	protected $jobUid;

	/** @var string */
	protected $name = '';

	/** @var int */
	protected $createdAt;

	/** @var int */
	protected $updatedAt;

	/** @var int|null */
	protected $deletedAt;

	public function __construct() {
		$this->addType('createdAt', Types::BIGINT);
		$this->addType('updatedAt', Types::BIGINT);
		$this->addType('deletedAt', Types::BIGINT);
	}

	#[\Override]
	#[ReturnTypeWillChange]
	public function jsonSerialize() {
		return [
			'uid' => $this->getUid(),
			'jobUid' => $this->getJobUid(),
			'name' => $this->getName(),
			'createdAt' => $this->getCreatedAt(),
			'updatedAt' => $this->getUpdatedAt(),
			'deletedAt' => $this->getDeletedAt(),
		];
	}
}
