/**
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

/**
 * One grant of read/write access to a NocTurn job's full schedule, given to
 * another Nextcloud user. Mirrors the server entity in
 * lib/Db/NocturnJobShare.php.
 *
 * @class
 */
export default class NocturnJobShare {

	static PERMISSION_READ = 'READ'
	static PERMISSION_WRITE = 'WRITE'

	constructor(data = {}) {
		/** @member {string} */
		this.jobUid = data.jobUid ?? ''
		/** @member {string} */
		this.ownerId = data.ownerId ?? ''
		/** @member {string} */
		this.sharedWith = data.sharedWith ?? ''
		/** @member {string} */
		this.permission = data.permission ?? NocturnJobShare.PERMISSION_READ
		/** @member {?number} */
		this.createdAt = data.createdAt ?? null
		/** @member {?number} */
		this.updatedAt = data.updatedAt ?? null
	}
}
