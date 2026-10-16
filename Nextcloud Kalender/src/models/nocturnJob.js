/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

/**
 * A NocTurn "job" groups shifts (or, in student mode, lessons) that belong
 * together, e.g. one employer or one school. Mirrors the server entity in
 * lib/Db/NocturnJob.php.
 *
 * @class
 */
export default class NocturnJob {

	static MODE_SHIFT = 'SHIFT'
	static MODE_STUDENT = 'STUDENT'

	constructor(data = {}) {
		/** @member {?string} */
		this.uid = data.uid ?? null
		/** @member {string} */
		this.name = data.name ?? ''
		/** @member {string} */
		this.mode = data.mode ?? NocturnJob.MODE_SHIFT
		/** @member {?number} */
		this.createdAt = data.createdAt ?? null
		/** @member {?number} */
		this.updatedAt = data.updatedAt ?? null
		/** @member {?number} */
		this.deletedAt = data.deletedAt ?? null
	}

	/**
	 * @return {NocturnJob}
	 */
	static createDefault() {
		return new NocturnJob({ name: '', mode: NocturnJob.MODE_SHIFT })
	}

	/**
	 * @return {NocturnJob}
	 */
	clone() {
		return new NocturnJob({ ...this })
	}
}
