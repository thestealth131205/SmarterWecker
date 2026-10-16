/**
 * SPDX-FileCopyrightText: 2020 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import { getFirstDay, getLanguage, isRTL, translate as t } from '@nextcloud/l10n'

/**
 * Returns localization settings for the FullCalender package.
 *
 * @see https://fullcalendar.io/docs
 * @return {object}
 */
function getFullCalendarLocale() {
	return {
		firstDay: getFirstDay(),
		locale: getLanguage(),
		direction: isRTL() ? 'rtl' : 'ltr',
		// TRANSLATORS W is an abbreviation for Week
		weekText: t('nocturnkalender', 'W'),
		allDayText: t('nocturnkalender', 'All day'),
		moreLinkText: (n) => t('nocturnkalender', '%n more', {}, n),
		noEventsText: t('nocturnkalender', 'No events to display'),
	}
}

export {
	getFullCalendarLocale,
}
