<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\Calendar\Migration;

use Closure;
use OCP\DB\ISchemaWrapper;
use OCP\DB\Types;
use OCP\Migration\IOutput;
use OCP\Migration\SimpleMigrationStep;

/**
 * Adds the NocTurn shift/rotation schedule tables (jobs, shifts, rotations,
 * rotation days, generated/manual occurrences) so they can be managed and
 * synced bidirectionally with the NocTurn Android app.
 */
class Version6070Date20261015120000 extends SimpleMigrationStep {
	/**
	 * @param IOutput $output
	 * @param Closure $schemaClosure The `\Closure` returns a `ISchemaWrapper`
	 * @param array $options
	 * @return null|ISchemaWrapper
	 */
	#[\Override]
	public function changeSchema(IOutput $output, Closure $schemaClosure, array $options): ?ISchemaWrapper {
		$schema = $schemaClosure();

		if (!$schema->hasTable('calendar_noct_jobs')) {
			$table = $schema->createTable('calendar_noct_jobs');
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
			$table->addColumn('name', Types::STRING, [
				'notnull' => true,
				'length' => 255,
			]);
			// JobMode [enum] - SHIFT or STUDENT (weekly timetable)
			$table->addColumn('mode', Types::STRING, [
				'notnull' => true,
				'length' => 16,
				'default' => 'SHIFT',
			]);
			$table->addColumn('created_at', Types::BIGINT, [
				'notnull' => true,
			]);
			$table->addColumn('updated_at', Types::BIGINT, [
				'notnull' => true,
			]);
			// Tombstone for sync: null = alive, timestamp = soft-deleted at
			$table->addColumn('deleted_at', Types::BIGINT, [
				'notnull' => false,
			]);

			$table->setPrimaryKey(['id']);
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_jobs_user_uid_uniq');
			$table->addIndex(['user_id', 'updated_at'], 'noct_jobs_user_updated_idx');
		}

		if (!$schema->hasTable('calendar_noct_shifts')) {
			$table = $schema->createTable('calendar_noct_shifts');
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
			$table->addColumn('name', Types::STRING, [
				'notnull' => true,
				'length' => 255,
			]);
			// Hex color, e.g. #FF9800
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
			// "Frühestes Wecken" offset before shift start, in minutes
			$table->addColumn('earliest_wake_offset_minutes', Types::INTEGER, [
				'notnull' => true,
				'default' => 0,
			]);
			// "Spätestens aufstehen" offset before shift start, in minutes
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
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_shifts_user_uid_uniq');
			$table->addIndex(['user_id', 'job_uid'], 'noct_shifts_user_job_idx');
			$table->addIndex(['user_id', 'updated_at'], 'noct_shifts_user_updated_idx');
		}

		if (!$schema->hasTable('calendar_noct_rotations')) {
			$table = $schema->createTable('calendar_noct_rotations');
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
			$table->addColumn('name', Types::STRING, [
				'notnull' => true,
				'length' => 255,
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
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_rotations_user_uid_uniq');
			$table->addIndex(['user_id', 'job_uid'], 'noct_rotations_user_job_idx');
			$table->addIndex(['user_id', 'updated_at'], 'noct_rot_user_updated_idx');
		}

		if (!$schema->hasTable('calendar_noct_rot_days')) {
			$table = $schema->createTable('calendar_noct_rot_days');
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
			$table->addColumn('rotation_uid', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			// Position within the rotation pattern, 0-based
			$table->addColumn('day_index', Types::INTEGER, [
				'notnull' => true,
			]);
			// null = free/no shift on this day of the pattern
			$table->addColumn('shift_uid', Types::STRING, [
				'notnull' => false,
				'length' => 64,
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
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_rotdays_user_uid_uniq');
			$table->addIndex(['user_id', 'rotation_uid', 'day_index'], 'noct_rotdays_rotation_idx');
			$table->addIndex(['user_id', 'updated_at'], 'noct_rotdays_user_updated_idx');
		}

		if (!$schema->hasTable('calendar_noct_occurr')) {
			$table = $schema->createTable('calendar_noct_occurr');
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
			// Days since epoch (matches Kotlin java.time.LocalDate#toEpochDay)
			$table->addColumn('epoch_day', Types::BIGINT, [
				'notnull' => true,
			]);
			// null = free day
			$table->addColumn('shift_uid', Types::STRING, [
				'notnull' => false,
				'length' => 64,
			]);
			// Set when generated by applying a rotation template, null for manual entries
			$table->addColumn('rotation_uid', Types::STRING, [
				'notnull' => false,
				'length' => 64,
			]);
			// OccurrenceSource [enum] - "rotation" (auto-generated) or "manual"
			$table->addColumn('source', Types::STRING, [
				'notnull' => true,
				'length' => 16,
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
			$table->addUniqueIndex(['user_id', 'uid'], 'noct_occurr_user_uid_uniq');
			$table->addIndex(['user_id', 'job_uid', 'epoch_day'], 'noct_occurr_job_day_idx');
			$table->addIndex(['user_id', 'rotation_uid'], 'noct_occurr_rotation_idx');
			$table->addIndex(['user_id', 'updated_at'], 'noct_occurr_user_updated_idx');
		}

		return $schema;
	}
}
