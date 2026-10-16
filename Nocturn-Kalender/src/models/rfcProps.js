/**
 * SPDX-FileCopyrightText: 2019 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import { translate as t } from '@nextcloud/l10n'
import { getDefaultCategories } from '@/defaults/defaultCategories.js'

/**
 * Gets all supported RFC properties
 *
 * @return {{
 *   accessClass: {readableName: string, icon: string, options: {value: string, label: string}[], multiple: boolean, info: string, defaultValue: string},
 *   location: {readableName: string, placeholder: string, icon: string},
 *   description: {readableName: string, placeholder: string, icon: string, defaultNumberOfRows: number},
 *   status: {readableName: string, icon: string, options: {value: string, label: string}[], multiple: boolean, info: string, defaultValue: string},
 *   timeTransparency: {readableName: string, icon: string, multiple: boolean, info: string, options: {value: string, label: string}[], defaultValue: string},
 *   categories: {readableName: string, icon: string, multiple: boolean, info: string, placeholder: string, tagPlaceholder: string, options: {value: string, label: string}[]},
 *   color: {readableName: string, icon: string, multiple: boolean, info: string},
 * }}
 */
function getRFCProperties() {
	return {
		/**
		 * https://tools.ietf.org/html/rfc5545#section-3.8.1.3
		 */
		accessClass: {
			readableName: t('nocturnkalender', 'When shared show'),
			icon: 'Eye',
			options: [
				{ value: 'PUBLIC', label: t('nocturnkalender', 'When shared show full event') },
				{ value: 'CONFIDENTIAL', label: t('nocturnkalender', 'When shared show only busy') },
				{ value: 'PRIVATE', label: t('nocturnkalender', 'When shared hide this event') },
			],
			multiple: false,
			info: t('nocturnkalender', 'The visibility of this event in read-only shared calendars.'),
			defaultValue: 'PUBLIC',
		},
		/**
		 * https://tools.ietf.org/html/rfc5545#section-3.8.1.7
		 */
		location: {
			readableName: t('nocturnkalender', 'Location'),
			placeholder: t('nocturnkalender', 'Add a location'),
			icon: 'MapMarker',
		},
		/**
		 * https://tools.ietf.org/html/rfc5545#section-3.8.1.5
		 */
		description: {
			readableName: t('nocturnkalender', 'Description'),
			placeholder: t('nocturnkalender', 'Add a description\n'
			+ '\n'
			+ '- What is this meeting about\n'
			+ '- Agenda items\n'
			+ '- Anything participants need to prepare'),
			icon: 'TextBoxOutline',
			defaultNumberOfRows: 2,
		},
		/**
		 * https://tools.ietf.org/html/rfc5545#section-3.8.1.11
		 */
		status: {
			readableName: t('nocturnkalender', 'Status'),
			icon: 'Check',
			options: [
				{ value: 'CONFIRMED', label: t('nocturnkalender', 'Confirmed') },
				{ value: 'TENTATIVE', label: t('nocturnkalender', 'Tentative') },
				{ value: 'CANCELLED', label: t('nocturnkalender', 'Canceled') },
			],
			multiple: false,
			info: t('nocturnkalender', 'Confirmation about the overall status of the event.'),
			defaultValue: 'CONFIRMED',
		},
		/**
		 * https://tools.ietf.org/html/rfc5545#section-3.8.2.7
		 */
		timeTransparency: {
			readableName: t('nocturnkalender', 'Show as'),
			icon: 'Briefcase',
			multiple: false,
			info: t('nocturnkalender', 'Take this event into account when calculating free-busy information.'),
			options: [
				{ value: 'TRANSPARENT', label: t('nocturnkalender', 'Free') },
				{ value: 'OPAQUE', label: t('nocturnkalender', 'Busy') },
			],
			defaultValue: 'TRANSPARENT',
		},
		/**
		 * https://tools.ietf.org/html/rfc5545#section-3.8.1.2
		 */
		categories: {
			readableName: t('nocturnkalender', 'Categories'),
			icon: 'Tag',
			multiple: true,
			info: t('nocturnkalender', 'Categories help you to structure and organize your events.'),
			placeholder: t('nocturnkalender', 'Search or add categories'),
			tagPlaceholder: t('nocturnkalender', 'Add this as a new category'),
			options: getDefaultCategories(),
		},
		/**
		 * https://tools.ietf.org/html/rfc7986#section-5.9
		 */
		color: {
			readableName: t('nocturnkalender', 'Custom color'),
			icon: 'EyedropperVariant',
			multiple: false,
			info: t('nocturnkalender', 'Special color of this event. Overrides the calendar-color.'),
		},
	}
}

export {
	getRFCProperties,
}
