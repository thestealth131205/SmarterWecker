/**
 * SPDX-FileCopyrightText: 2019 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import { loadState } from '@nextcloud/initial-state'
import { linkTo } from '@nextcloud/router'

/**
 * Get the settings provided as initial state by the server.
 *
 * The calendar initial state is not available on every page - the editor can be
 * opened from any app - so every value falls back to the server side default.
 *
 * @return {object} Settings as expected by the `loadSettingsFromServer` action
 */
export function getSettingsFromInitialState() {
	return {
		appVersion: loadState('nocturnkalender', 'app_version', ''),
		eventLimit: loadState('nocturnkalender', 'event_limit', true),
		firstRun: loadState('nocturnkalender', 'first_run', false),
		showWeekends: loadState('nocturnkalender', 'show_weekends', true),
		showWeekNumbers: loadState('nocturnkalender', 'show_week_numbers', false),
		skipPopover: loadState('nocturnkalender', 'skip_popover', false),
		slotDuration: loadState('nocturnkalender', 'slot_duration', '00:30:00'),
		defaultReminderPartDay: loadState('nocturnkalender', 'default_reminder_part_day', 'none'),
		defaultReminderFullDay: loadState('nocturnkalender', 'default_reminder_full_day', 'none'),
		talkEnabled: loadState('nocturnkalender', 'talk_enabled', false),
		tasksEnabled: loadState('nocturnkalender', 'tasks_enabled', false),
		timezone: loadState('nocturnkalender', 'timezone', 'automatic'),
		showTasks: loadState('nocturnkalender', 'show_tasks', false),
		showDeclined: loadState('nocturnkalender', 'show_declined', true),
		hideEventExport: loadState('nocturnkalender', 'hide_event_export', false),
		forceEventAlarmType: loadState('nocturnkalender', 'force_event_alarm_type', false),
		disableAppointments: loadState('nocturnkalender', 'disable_appointments', false),
		canSubscribeLink: loadState('nocturnkalender', 'can_subscribe_link', false),
		attachmentsFolder: loadState('nocturnkalender', 'attachments_folder', '/Calendar'),
		showResources: loadState('nocturnkalender', 'show_resources', true),
		publicCalendars: loadState('nocturnkalender', 'publicCalendars', []),
		tasksSidebar: loadState('nocturnkalender', 'tasks_sidebar', true),
	}
}

/**
 * Get URL to modify config-key
 *
 * @param {string} key URL of config-key to modify
 * @return {string}
 */
export function getLinkToConfig(key) {
	return [
		linkTo('nocturnkalender', 'index.php'),
		'v1/config',
		key,
	].join('/')
}
