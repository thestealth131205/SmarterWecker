/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

/**
 * A NocTurn to-do item - unlike an appointment it has no fixed time range,
 * just a due day (and optionally a due time), plus a completed flag.
 * Mirrors the server entity in lib/Db/NocturnTask.php.
 *
 * @class
 */
export default class NocturnTask {

	constructor(data = {}) {
		/** @member {?string} */
		this.uid = data.uid ?? null
		/** @member {string} */
		this.title = data.title ?? ''
		/** @member {string} */
		this.notes = data.notes ?? ''
		/** @member {number} Days since 1970-01-01 UTC */
		this.dueEpochDay = data.dueEpochDay ?? Math.floor(Date.now() / 86400000)
		/** @member {?number} */
		this.dueEpochMillis = data.dueEpochMillis ?? null
		/** @member {boolean} */
		this.allDay = data.allDay ?? true
		/** @member {boolean} */
		this.completed = data.completed ?? false
		/** @member {string} */
		this.color = data.color ?? '#4CAF50'
		/** @member {?number} */
		this.createdAt = data.createdAt ?? null
		/** @member {?number} */
		this.updatedAt = data.updatedAt ?? null
		/** @member {?number} */
		this.deletedAt = data.deletedAt ?? null
	}

	/**
	 * @return {NocturnTask}
	 */
	static createDefault() {
		return new NocturnTask({ title: '' })
	}

	/**
	 * @return {Date}
	 */
	get dueDate() {
		return new Date(this.dueEpochDay * 86400000)
	}

	/**
	 * @return {NocturnTask}
	 */
	clone() {
		return new NocturnTask({ ...this })
	}
}
