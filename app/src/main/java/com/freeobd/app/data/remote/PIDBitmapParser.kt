/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.freeobd.app.data.remote

/**
 * SAE J1979 PID/InfoType bitmap parser.
 *
 * All Mode 01, Mode 02, and Mode 09 discovery responses use the same
 * bitmap encoding: 4 data bytes where each bit represents one PID/InfoType
 * (bit 7 of byte 0 = ID 0x01, bit 6 = ID 0x02, ..., bit 0 of byte 3 = ID 0x20).
 *
 * @param data   Raw data bytes from the discovery response (after extractDataBytes).
 * @param offset PID/InfoType offset to add (segment * 1 for per-segment queries, 0 for Mode 09).
 * @return Set of supported IDs.
 */
fun parsePidBitmap(data: ByteArray, offset: Int = 0): Set<Int> {
    val supported = mutableSetOf<Int>()
    for (byteIdx in data.indices) {
        val byte = data[byteIdx].toInt() and 0xFF
        if (byte == 0) continue
        for (bit in 0..7) {
            if ((byte and (1 shl (7 - bit))) != 0) {
                supported.add(byteIdx * 8 + bit + 1 + offset)
            }
        }
    }
    return supported
}
