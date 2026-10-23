<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Migration;

use Closure;
use OCP\DB\ISchemaWrapper;
use OCP\DB\Types;
use OCP\Migration\IOutput;
use OCP\Migration\SimpleMigrationStep;

/**
 * Adds the tables backing the NocTurn weekly timetable (student mode lesson
 * periods) and the NocTurn task list, so both sync bidirectionally with the
 * NocTurn Android app the same way jobs/shifts/rotations already do.
 */
class Version6070Date20261023000000 extends SimpleMigrationStep {
	/**
	 * @param IOutput $output
	 * @param Closure $schemaClosure The `\Closure` returns a `ISchemaWrapper`
	 * @param array $options
	 * @return null|ISchemaWrapper
	 */
	#[\Override]
	public function changeSchema(IOutput $output, Closure $schemaClosure, array $options): ?ISchemaWrapper {
		$schema = $schemaClosure();

		if (!$schema->hasTable('calendar_noct_lessons')) {
			$table = $schema->createTable('calendar_noct_lessons');
			$table->addColumn('id', Types::BIGINT, [
				'autoincrement' => true,
				'notnull' => true,
				'length' => 11,
				'unsigned' => true,
			]);
			$table->addColumn('uid', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			$table->addColumn('user_id', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			$table->addColumn('job_uid', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			// 0 = Monday .. 6 = Sunday (matches NocTurn Android's LessonEntity.weekday). Needs an
			// explicit DB default: 0 is both the PHP property default AND a common real value
			// (Monday), so Nextcloud's Entity::setter() would skip it from the INSERT column list
			// whenever it's set to exactly 0 (see the day_index fix in Version6070Date20261015120000
			// for the same class of bug) - without a DB-level default that omission would 1364 on a
			// strict MariaDB instead of silently falling back to 0.
			$table->addColumn('weekday', Types::SMALLINT, [
				'notnull' => true,
				'default' => 0,
			]);
			$table->addColumn('name', Types::STRING, [
				'notnull' => true,
				'length' => 255,
			]);
			$table->addColumn('color', Types::STRING, [
				'notnull' => true,
				'length' => 16,
			]);
			$table->addColumn('start_hour', Types::SMALLINT, [
				'notnull' => true,
				'default' => 0,
			]);
			$table->addColumn('start_minute', Types::SMALLINT, [
				'notnull' => true,
				'default' => 0,
			]);
			$table->addColumn('duration_minutes', Types::INTEGER, [
				'notnull' => true,
				'default' => 0,
			]);
			$table->addColumn('earliest_wake_offset_minutes', Types::INTEGER, [
				'notnull' => true,
				'default' => 0,
			]);
			$table->addColumn('latest_wakeup_offset_minutes', Types::INTEGER, [
				'notnull' => true,
				'default' => 0,
			]);
			$table->addColumn('created_at', Types::BIGINT, [
				'notnull' => true,
			]);
			$table->addColumn('updated_at', Types::BIGINT, [
				'notnull' => true,
			]);
			$table->addColumn('deleted_at', Types::BIGINT, [
				'notnull' => false,
			]);

			$table->setPrimaryKey(['id']);
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_lessons_user_uid_uniq');
			$table->addIndex(['user_id', 'job_uid'], 'noct_lessons_user_job_idx');
			$table->addIndex(['user_id', 'updated_at'], 'noct_lessons_user_updated_idx');
		}

		if (!$schema->hasTable('calendar_noct_tasks')) {
			$table = $schema->createTable('calendar_noct_tasks');
			$table->addColumn('id', Types::BIGINT, [
				'autoincrement' => true,
				'notnull' => true,
				'length' => 11,
				'unsigned' => true,
			]);
			$table->addColumn('uid', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			$table->addColumn('user_id', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			$table->addColumn('title', Types::STRING, [
				'notnull' => true,
				'length' => 255,
			]);
			$table->addColumn('notes', Types::TEXT, [
				'notnull' => false,
			]);
			// Days since epoch (matches Kotlin java.time.LocalDate#toEpochDay)
			$table->addColumn('due_epoch_day', Types::BIGINT, [
				'notnull' => true,
			]);
			// null when the task is all-day (no specific due time)
			$table->addColumn('due_epoch_millis', Types::BIGINT, [
				'notnull' => false,
			]);
			$table->addColumn('all_day', Types::BOOLEAN, [
				'notnull' => true,
				'default' => true,
			]);
			$table->addColumn('completed', Types::BOOLEAN, [
				'notnull' => true,
				'default' => false,
			]);
			// Hex color, e.g. #4CAF50
			$table->addColumn('color', Types::STRING, [
				'notnull' => true,
				'length' => 16,
				'default' => '#4CAF50',
			]);
			$table->addColumn('created_at', Types::BIGINT, [
				'notnull' => true,
			]);
			$table->addColumn('updated_at', Types::BIGINT, [
				'notnull' => true,
			]);
			$table->addColumn('deleted_at', Types::BIGINT, [
				'notnull' => false,
			]);

			$table->setPrimaryKey(['id']);
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_tasks_user_uid_uniq');
			$table->addIndex(['user_id', 'updated_at'], 'noct_tasks_user_updated_idx');
		}

		return $schema;
	}
}
