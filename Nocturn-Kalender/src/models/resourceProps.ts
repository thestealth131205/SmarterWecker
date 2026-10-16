/**
 * SPDX-FileCopyrightText: 2021 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import { translate as t } from '@nextcloud/l10n'

/**
 * Return all supported room types
 *
 * @return List of supported room types
 */
export function getAllRoomTypes() {
	return [
		{ value: 'meeting-room', label: t('nocturnkalender', 'Meeting room') },
		{ value: 'lecture-hall', label: t('nocturnkalender', 'Lecture hall') },
		{ value: 'seminar-room', label: t('nocturnkalender', 'Seminar room') },
		{ value: 'other', label: t('nocturnkalender', 'Other') },
	]
}

/**
 * Format room type as a human readable and localized string
 *
 * @param value Raw room type
 * @return Human readable and localized room type or null if given raw value is invalid
 */
export function formatRoomType(value: string): string | null {
	const option = getAllRoomTypes().find((option) => option.value === value)
	return option?.label ?? null
}

/**
 * Known room features and their localized labels.
 *
 * Unknown features are shown as-is: room backends are free to publish their
 * own, and an untranslated raw value beats a mangled one.
 *
 * Evaluated lazily so that t() is not called at module import time.
 *
 * @return Map of raw feature value to human readable and localized label
 */
function getFeatureLabels(): Record<string, string> {
	return {
		PROJECTOR: t('nocturnkalender', 'Projector'),
		WHITEBOARD: t('nocturnkalender', 'Whiteboard'),
		'WHEELCHAIR-ACCESSIBLE': t('nocturnkalender', 'Wheelchair accessible'),
		'AV-EQUIPMENT': t('nocturnkalender', 'Audio-visual equipment'),
		PHONE: t('nocturnkalender', 'Phone'),
		'VIDEO-CONFERENCING': t('nocturnkalender', 'Video conferencing'),
		TV: t('nocturnkalender', 'TV'),
	}
}

/**
 * Format a room feature as a human readable and localized string
 *
 * @param feature Raw feature value as published by the room backend
 * @return Localized label, or the raw value if the feature is not known
 */
export function formatRoomFeature(feature: string): string {
	return getFeatureLabels()[feature.toUpperCase()] ?? feature
}
