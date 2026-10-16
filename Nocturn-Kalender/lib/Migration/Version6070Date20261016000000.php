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
 * Adds the table backing NocTurn job sharing: lets a job's owner grant
 * another Nextcloud user read (or read/write) access to one job's full
 * schedule - shifts, rotations and their generated/manual occurrences -
 * without duplicating any of that data into the grantee's own rows.
 */
class Version6070Date20261016000000 extends SimpleMigrationStep {
	/**
	 * @param IOutput $output
	 * @param Closure $schemaClosure The `\Closure` returns a `ISchemaWrapper`
	 * @param array $options
	 * @return null|ISchemaWrapper
	 */
	#[\Override]
	public function changeSchema(IOutput $output, Closure $schemaClosure, array $options): ?ISchemaWrapper {
		$schema = $schemaClosure();

		if (!$schema->hasTable('calendar_noct_job_shares')) {
			$table = $schema->createTable('calendar_noct_job_shares');
			$table->addColumn('id', Types::BIGINT, [
				'autoincrement' => true,
				'notnull' => true,
				'length' => 11,
				'unsigned' => true,
			]);
			$table->addColumn('job_uid', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			// The job's owner, i.e. the `user_id` on calendar_noct_jobs et al.
			$table->addColumn('owner_id', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			// The Nextcloud user this job is shared with
			$table->addColumn('shared_with', Types::STRING, [
				'notnull' => true,
				'length' => 64,
			]);
			// SharePermission [enum] - READ or WRITE
			$table->addColumn('permission', Types::STRING, [
				'notnull' => true,
				'length' => 16,
				'default' => 'READ',
			]);
			$table->addColumn('created_at', Types::BIGINT, [
				'notnull' => true,
			]);
			$table->addColumn('updated_at', Types::BIGINT, [
				'notnull' => true,
			]);

			$table->setPrimaryKey(['id']);
			$table->addUniqueIndex(['owner_id', 'job_uid', 'shared_with'], 'noct_job_shares_uniq');
			$table->addIndex(['shared_with'], 'noct_job_shares_target_idx');
			$table->addIndex(['owner_id', 'job_uid'], 'noct_job_shares_owner_job_idx');
		}

		return $schema;
	}
}
