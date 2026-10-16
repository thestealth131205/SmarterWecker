/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

/**
 * A reusable rotation template (day-for-day pattern). Has no start date of
 * its own - it is only a pattern that gets applied to a concrete date range.
 * Mirrors the server entity in lib/Db/NocturnRotation.php; `days` (an
 * ordered array of shift uids, null = free day) is this app's own addition
 * on top of the plain entity, since the server keeps the pattern in a
 * separate table (NocturnRotationDay).
 *
 * @class
 */
export default class NocturnRotation {

	constructor(data = {}) {
		/** @member {?string} */
		this.uid = data.uid ?? null
		/** @member {string} */
		this.jobUid = data.jobUid ?? ''
		/** @member {string} */
		this.name = data.name ?? ''
		/** @member {Array<string|null>} */
		this.days = data.days ?? []
		/** @member {?number} */
		this.createdAt = data.createdAt ?? null
		/** @member {?number} */
		this.updatedAt = data.updatedAt ?? null
		/** @member {?number} */
		this.deletedAt = data.deletedAt ?? null
	}

	/**
	 * @param {string} jobUid Job the new rotation belongs to
	 * @return {NocturnRotation}
	 */
	static createDefault(jobUid) {
		return new NocturnRotation({ jobUid, name: '', days: [null, null, null, null, null, null, null] })
	}

	/**
	 * @return {NocturnRotation}
	 */
	clone() {
		return new NocturnRotation({ ...this, days: [...this.days] })
	}
}
