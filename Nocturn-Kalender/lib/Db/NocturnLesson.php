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
 * A single weekly timetable period belonging to a student-mode job, e.g.
 * "Mathe" Monday 07:45-08:30. Mirrors Android's `LessonEntity`.
 *
 * @method string getUid()
 * @method void setUid(string $uid)
 * @method string getUserId()
 * @method void setUserId(string $userId)
 * @method string getJobUid()
 * @method void setJobUid(string $jobUid)
 * @method int getWeekday()
 * @method void setWeekday(int $weekday)
 * @method string getName()
 * @method void setName(string $name)
 * @method string getColor()
 * @method void setColor(string $color)
 * @method int getStartHour()
 * @method void setStartHour(int $startHour)
 * @method int getStartMinute()
 * @method void setStartMinute(int $startMinute)
 * @method int getDurationMinutes()
 * @method void setDurationMinutes(int $durationMinutes)
 * @method int getEarliestWakeOffsetMinutes()
 * @method void setEarliestWakeOffsetMinutes(int $minutes)
 * @method int getLatestWakeupOffsetMinutes()
 * @method void setLatestWakeupOffsetMinutes(int $minutes)
 * @method int getCreatedAt()
 * @method void setCreatedAt(int $createdAt)
 * @method int getUpdatedAt()
 * @method void setUpdatedAt(int $updatedAt)
 * @method int|null getDeletedAt()
 * @method void setDeletedAt(?int $deletedAt)
 */
class NocturnLesson extends Entity implements JsonSerializable {
	/** @var string */
	protected $uid;

	/** @var string */
	protected $userId;

	/** @var string */
	protected $jobUid;

	/** @var int */
	protected $weekday = 0;

	/** @var string */
	protected $name = '';

	/** @var string */
	protected $color = '#2196F3';

	/** @var int */
	protected $startHour = 8;

	/** @var int */
	protected $startMinute = 0;

	/** @var int */
	protected $durationMinutes = 45;

	/** @var int */
	protected $earliestWakeOffsetMinutes = 0;

	/** @var int */
	protected $latestWakeupOffsetMinutes = 0;

	/** @var int */
	protected $createdAt;

	/** @var int */
	protected $updatedAt;

	/** @var int|null */
	protected $deletedAt;

	public function __construct() {
		$this->addType('weekday', Types::SMALLINT);
		$this->addType('startHour', Types::SMALLINT);
		$this->addType('startMinute', Types::SMALLINT);
		$this->addType('durationMinutes', Types::INTEGER);
		$this->addType('earliestWakeOffsetMinutes', Types::INTEGER);
		$this->addType('latestWakeupOffsetMinutes', Types::INTEGER);
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
			'weekday' => $this->getWeekday(),
			'name' => $this->getName(),
			'color' => $this->getColor(),
			'startHour' => $this->getStartHour(),
			'startMinute' => $this->getStartMinute(),
			'durationMinutes' => $this->getDurationMinutes(),
			'earliestWakeOffsetMinutes' => $this->getEarliestWakeOffsetMinutes(),
			'latestWakeupOffsetMinutes' => $this->getLatestWakeupOffsetMinutes(),
			'createdAt' => $this->getCreatedAt(),
			'updatedAt' => $this->getUpdatedAt(),
			'deletedAt' => $this->getDeletedAt(),
		];
	}
}
