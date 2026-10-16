<?php

declare(strict_types=1);
/**
 * SPDX-FileCopyrightText: 2019 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

namespace OCA\NocturnKalender\AppInfo;

use OCA\NocturnKalender\Dashboard\CalendarWidget;
use OCA\NocturnKalender\Events\BeforeAppointmentBookedEvent;
use OCA\NocturnKalender\Listener\AppMenuActionListener;
use OCA\NocturnKalender\Listener\AppointmentBookedListener;
use OCA\NocturnKalender\Listener\CalendarReferenceListener;
use OCA\NocturnKalender\Listener\EditorInitialStateListener;
use OCA\NocturnKalender\Listener\NotifyPushListener;
use OCA\NocturnKalender\Listener\UserDeletedListener;
use OCA\NocturnKalender\Notification\Notifier;
use OCA\NocturnKalender\Profile\AppointmentsAction;
use OCA\NocturnKalender\Reference\ReferenceProvider;
use OCA\NocturnKalender\UserMigration\Migrator;
use OCP\AppFramework\App;
use OCP\AppFramework\Bootstrap\IBootContext;
use OCP\AppFramework\Bootstrap\IBootstrap;
use OCP\AppFramework\Bootstrap\IRegistrationContext;
use OCP\AppFramework\Http\Events\BeforeTemplateRenderedEvent;
use OCP\Calendar\Events\CalendarObjectCreatedEvent;
use OCP\Calendar\Events\CalendarObjectDeletedEvent;
use OCP\Calendar\Events\CalendarObjectUpdatedEvent;
use OCP\Collaboration\Reference\RenderReferenceEvent;
use OCP\IUserSession;
use OCP\Navigation\Events\LoadAdditionalEntriesEvent;
use OCP\User\Events\UserDeletedEvent;
use OCP\Util;
use Psr\Container\ContainerInterface;

class Application extends App implements IBootstrap {
	/** @var string */
	public const APP_ID = 'nocturnkalender';

	/**
	 * @param array $params
	 */
	public function __construct(array $params = []) {
		parent::__construct(self::APP_ID, $params);
	}

	/**
	 * @inheritDoc
	 */
	#[\Override]
	public function register(IRegistrationContext $context): void {
		$context->registerCapability(Capabilities::class);

		$context->registerDashboardWidget(CalendarWidget::class);

		$context->registerProfileLinkAction(AppointmentsAction::class);

		$context->registerReferenceProvider(ReferenceProvider::class);

		$context->registerEventListener(BeforeAppointmentBookedEvent::class, AppointmentBookedListener::class);
		$context->registerEventListener(UserDeletedEvent::class, UserDeletedListener::class);
		$context->registerEventListener(RenderReferenceEvent::class, CalendarReferenceListener::class);
		if (AppMenuActionListener::supportsAppMenuActions()) {
			// The editor of the new event dialog can be opened on any page
			$context->registerEventListener(BeforeTemplateRenderedEvent::class, EditorInitialStateListener::class);
			// The app navigation action
			$context->registerEventListener(LoadAdditionalEntriesEvent::class, AppMenuActionListener::class);
		}

		$context->registerEventListener(CalendarObjectCreatedEvent::class, NotifyPushListener::class);
		$context->registerEventListener(CalendarObjectUpdatedEvent::class, NotifyPushListener::class);
		$context->registerEventListener(CalendarObjectDeletedEvent::class, NotifyPushListener::class);

		$context->registerNotifierService(Notifier::class);

		$context->registerUserMigrator(Migrator::class);
	}

	/**
	 * @inheritDoc
	 */
	#[\Override]
	public function boot(IBootContext $context): void {
		$this->addContactsMenuScript($context->getServerContainer());
	}

	private function addContactsMenuScript(ContainerInterface $container): void {
		// User needs to be logged in to fetch availability -> disable the feature otherwise
		/** @var IUserSession $userSession */
		$userSession = $container->get(IUserSession::class);
		if (!$userSession->isLoggedIn()) {
			return;
		}

		// The contacts menu/avatar is potentially shown everywhere so an event based loading
		// mechanism doesn't make sense here
		Util::addScript(self::APP_ID, 'nocturnkalender-contacts-menu');
		Util::addStyle(self::APP_ID, 'nocturnkalender-contacts-menu');
	}

}
