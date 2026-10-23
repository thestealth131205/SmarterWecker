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
 * A NocTurn to-do item - unlike an appointment it has no fixed time range,
 * just a due day (and optionally a due time), plus a completed flag.
 * Mirrors Android's `TaskEntity`.
 *
 * @method string getUid()
 * @method void setUid(string $uid)
 * @method string getUserId()
 * @method void setUserId(string $userId)
 * @method string getTitle()
 * @method void setTitle(string $title)
 * @method string|null getNotes()
 * @method void setNotes(?string $notes)
 * @method int getDueEpochDay()
 * @method void setDueEpochDay(int $dueEpochDay)
 * @method int|null getDueEpochMillis()
 * @method void setDueEpochMillis(?int $dueEpochMillis)
 * @method bool getAllDay()
 * @method void setAllDay(bool $allDay)
 * @method bool getCompleted()
 * @method void setCompleted(bool $completed)
 * @method string getColor()
 * @method void setColor(string $color)
 * @method int getCreatedAt()
 * @method void setCreatedAt(int $createdAt)
 * @method int getUpdatedAt()
 * @method void setUpdatedAt(int $updatedAt)
 * @method int|null getDeletedAt()
 * @method void setDeletedAt(?int $deletedAt)
 */
class NocturnTask extends Entity implements JsonSerializable {
	/** @var string */
	protected $uid;

	/** @var string */
	protected $userId;

	/** @var string */
	protected $title = '';

	/** @var string|null */
	protected $notes;

	/** @var int */
	protected $dueEpochDay;

	/** @var int|null */
	protected $dueEpochMillis;

	/** @var bool */
	protected $allDay = true;

	/** @var bool */
	protected $completed = false;

	/** @var string */
	protected $color = '#4CAF50';

	/** @var int */
	protected $createdAt;

	/** @var int */
	protected $updatedAt;

	/** @var int|null */
	protected $deletedAt;

	public function __construct() {
		$this->addType('dueEpochDay', Types::BIGINT);
		$this->addType('dueEpochMillis', Types::BIGINT);
		$this->addType('allDay', Types::BOOLEAN);
		$this->addType('completed', Types::BOOLEAN);
		$this->addType('createdAt', Types::BIGINT);
		$this->addType('updatedAt', Types::BIGINT);
		$this->addType('deletedAt', Types::BIGINT);
	}

	#[\Override]
	#[ReturnTypeWillChange]
	public function jsonSerialize() {
		return [
			'uid' => $this->getUid(),
			'title' => $this->getTitle(),
			'notes' => $this->getNotes(),
			'dueEpochDay' => $this->getDueEpochDay(),
			'dueEpochMillis' => $this->getDueEpochMillis(),
			'allDay' => $this->getAllDay(),
			'completed' => $this->getCompleted(),
			'color' => $this->getColor(),
			'createdAt' => $this->getCreatedAt(),
			'updatedAt' => $this->getUpdatedAt(),
			'deletedAt' => $this->getDeletedAt(),
		];
	}
}
