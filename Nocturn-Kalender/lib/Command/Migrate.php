<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Command;

use OC\DB\Connection;
use OC\DB\MigrationService;
use OC\Migration\ConsoleOutput;
use OCA\NocturnKalender\AppInfo\Application;
use OCP\App\IAppManager;
use OCP\IConfig;
use OCP\Server;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;

/**
 * Runs any pending NocTurn-Kalender-specific DB migrations (the
 * `calendar_noct_*` tables) without touching Nextcloud core or any other
 * app.
 *
 * Needed because `occ upgrade` only detects migrations for THIS app via a
 * version-string diff between `appinfo/info.xml` and the previously stored
 * `oc_appconfig.installed_version` - and running the general `occ upgrade`
 * on every release-deploy would risk also triggering an unrelated, unwanted
 * full Nextcloud core upgrade if one happens to be pending on the server at
 * that moment. This command instead replicates exactly the migration step
 * of `\OC\Installer::installAppLastSteps()` (see there), scoped to just
 * `nocturnkalender`, so `scripts/release-deploy.sh` can safely run it after
 * every file deploy.
 */
class Migrate extends Command {
	public function __construct(
		private IConfig $config,
		private IAppManager $appManager,
	) {
		parent::__construct();
	}

	#[\Override]
	protected function configure(): void {
		$this->setName('nocturnkalender:migrate')
			->setDescription('Runs pending NocTurn-Kalender DB migrations (calendar_noct_* tables)');
	}

	#[\Override]
	protected function execute(InputInterface $input, OutputInterface $output): int {
		$appId = Application::APP_ID;
		$previousVersion = $this->config->getAppValue($appId, 'installed_version', '');

		$migrationService = new MigrationService($appId, Server::get(Connection::class));
		$migrationService->setOutput(new ConsoleOutput($output));
		$migrationService->migrate('latest', $previousVersion === '');

		// Liest die Version frisch aus appinfo/info.xml (zweiter Parameter = kein Cache) - exakt
		// wie \OC\Installer::installAppLastSteps() das installed_version-Appconfig nach einer
		// Migration aktuell hält.
		$currentVersion = $this->appManager->getAppVersion($appId, false);
		$this->config->setAppValue($appId, 'installed_version', $currentVersion);

		$output->writeln("<info>NocTurn-Kalender-Migrationen auf Version {$currentVersion} ausgeführt.</info>");

		return 0;
	}
}
