<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\Calendar\Db;

use JsonSerializable;
use OCP\AppFramework\Db\Entity;
use OCP\DB\Types;
use ReturnTypeWillChange;

/**
 * A NocTurn "job" groups shifts (or, in student mode, lessons) that belong
 * together, e.g. one employer or one school.
 *
 * @method string getUid()
 * @method void setUid(string $uid)
 * @method string getUserId()
 * @method void setUserId(string $userId)
 * @method string getName()
 * @method void setName(string $name)
 * @method string getMode()
 * @method void setMode(string $mode)
 * @method int getCreatedAt()
 * @method void setCreatedAt(int $createdAt)
 * @method int getUpdatedAt()
 * @method void setUpdatedAt(int $updatedAt)
 * @method int|null getDeletedAt()
 * @method void setDeletedAt(?int $deletedAt)
 */
class NocturnJob extends Entity implements JsonSerializable {
	public const MODE_SHIFT = 'SHIFT';
	public const MODE_STUDENT = 'STUDENT';

	/** @var string */
	protected $uid;

	/** @var string */
	protected $userId;

	/** @var string */
	protected $name = '';

	/** @var string */
	protected $mode = self::MODE_SHIFT;

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
			'name' => $this->getName(),
			'mode' => $this->getMode(),
			'createdAt' => $this->getCreatedAt(),
			'updatedAt' => $this->getUpdatedAt(),
			'deletedAt' => $this->getDeletedAt(),
		];
	}
}
