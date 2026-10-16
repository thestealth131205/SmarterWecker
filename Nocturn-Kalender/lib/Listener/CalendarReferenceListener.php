<?php

declare(strict_types=1);

/**
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\Listener;

use OCA\NocturnKalender\AppInfo\Application;
use OCA\NocturnKalender\Service\CalendarInitialStateService;
use OCP\Collaboration\Reference\RenderReferenceEvent;
use OCP\EventDispatcher\Event;
use OCP\EventDispatcher\IEventListener;
use OCP\Util;

/**
 * @template-implements IEventListener<Event|RenderReferenceEvent>
 */
class CalendarReferenceListener implements IEventListener {

	public function __construct(
		private CalendarInitialStateService $calendarInitialStateService,
	) {
	}

	#[\Override]
	public function handle(Event $event): void {
		if (!$event instanceof RenderReferenceEvent) {
			return;
		}

		$this->calendarInitialStateService->run();
		Util::addScript(Application::APP_ID, 'nocturnkalender-reference');
		Util::addStyle(Application::APP_ID, 'nocturnkalender-reference');
	}
}
