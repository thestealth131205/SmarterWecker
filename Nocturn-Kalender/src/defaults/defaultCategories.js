/**
 * SPDX-FileCopyrightText: 2019 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import { translate as t } from '@nextcloud/l10n'

export function getDefaultCategories() {
	// This list was taken from https://tools.ietf.org/html/rfc5545#section-5
	const values = [
		t('nocturnkalender', 'Anniversary'),
		t('nocturnkalender', 'Appointment'),
		t('nocturnkalender', 'Business'),
		t('nocturnkalender', 'Education'),
		t('nocturnkalender', 'Holiday'),
		t('nocturnkalender', 'Meeting'),
		t('nocturnkalender', 'Miscellaneous'),
		t('nocturnkalender', 'Non-working hours'),
		t('nocturnkalender', 'Not in office'),
		t('nocturnkalender', 'Personal'),
		t('nocturnkalender', 'Phone call'),
		t('nocturnkalender', 'Sick day'),
		t('nocturnkalender', 'Special occasion'),
		t('nocturnkalender', 'Travel'),
		t('nocturnkalender', 'Vacation'),
	]
	return values.map((value) => ({ value, label: value }))
}

export default getDefaultCategories
