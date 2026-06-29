package com.xlocalhost.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class LocalWebServer(
    private val context: Context,
    private val folderUri: Uri,
    port: Int = 8080,
    private val allowModification: Boolean = false,
) : NanoHTTPD(port) {

    // ── Session / captcha stores (shared across instances) ────────────────────
    data class SessionEntry(val username: String, val role: String, val expiresAt: Long)
    data class CaptchaEntry(val code: String, val expiresAt: Long)

    companion object {
        private val sessions  = ConcurrentHashMap<String, SessionEntry>()
        private val captchas  = ConcurrentHashMap<String, CaptchaEntry>()
        private const val SESSION_TTL  = 24L * 60 * 60 * 1000
        private const val CAPTCHA_TTL  = 5L  * 60 * 1000
        private const val COOKIE_NAME  = "session"

        /** Custom HTTP 415 — not in NanoHTTPD 2.3.1 enum */
        val STATUS_415: Response.IStatus = object : Response.IStatus {
            override fun getDescription() = "415 Unsupported Media Type"
            override fun getRequestStatus() = 415
        }

        /** Custom HTTP 420 — not in NanoHTTPD 2.3.1 enum */
        val STATUS_420: Response.IStatus = object : Response.IStatus {
            override fun getDescription() = "420 Method Failure"
            override fun getRequestStatus() = 420
        }

        fun fnv1a32(input: String): Long {
            var hash = 2166136261L
            for (b in input.toByteArray()) {
                hash = (hash xor (b.toLong() and 0xFF)) * 16777619L and 0xFFFFFFFFL
            }
            return hash
        }

        fun hashPassword(pw: String): String = fnv1a32(pw).toString(16)
    }

    // ── Route dispatch ────────────────────────────────────────────────────────
    override fun serve(session: IHTTPSession): Response {
        cleanExpired()
        val m   = session.method
        val uri = session.uri.trimEnd('/')

        return when {
            // Auth
            m == Method.GET  && uri == "/api/auth/captcha"  -> authCaptcha()
            m == Method.POST && uri == "/api/auth/register" -> authRegister(session)
            m == Method.POST && uri == "/api/auth/login"    -> authLogin(session)
            m == Method.POST && uri == "/api/auth/logout"   -> authLogout(session)
            // System
            m == Method.GET  && uri == "/api/system/status" -> systemStatus(session)
            // File
            m == Method.GET    && uri == "/api/fs/list"      -> fsListDir(session)
            m == Method.GET    && uri == "/api/fs/download"  -> fsDownload(session)
            m == Method.POST   && uri == "/api/fs/upload"    -> fsUpload(session)
            m == Method.DELETE && uri == "/api/fs/delete"    -> fsDelete(session)
            m == Method.POST   && uri == "/api/fs/rename"    -> fsRename(session)
            m == Method.POST   && uri == "/api/fs/mkdir"     -> fsMkdir(session)
            m == Method.GET    && uri == "/api/fs/stat"      -> fsStat(session)
            m == Method.POST   && uri == "/api/fs/move"      -> fsMove(session)
            m == Method.GET    && uri == "/api/fs/thumbnail" -> fsThumbnail(session)
            m == Method.GET    && uri == "/api/fs/zip"       -> fsZip(session)
            // DB
            m == Method.GET  && uri == "/api/db/schema"      -> dbSchema(session)
            m == Method.POST && uri == "/api/db/query"        -> dbQuery(session)
            uri.startsWith("/api/db/")                        -> dbRoute(session, m, uri)
            // Static / legacy browser
            else -> serveStatic(session)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AUTH API
    // ═══════════════════════════════════════════════════════════════════════════

    private fun authCaptcha(): Response {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code  = (1..5).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val cid   = java.util.UUID.randomUUID().toString()
        captchas[cid] = CaptchaEntry(code, System.currentTimeMillis() + CAPTCHA_TTL)

        val w = 160; val h = 50
        val bmp    = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(240, 240, 255))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rng   = java.util.Random()

        // Noise lines
        paint.color = Color.rgb(180, 180, 210)
        paint.strokeWidth = 1.5f
        repeat(8) {
            canvas.drawLine(rng.nextFloat() * w, rng.nextFloat() * h,
                            rng.nextFloat() * w, rng.nextFloat() * h, paint)
        }
        // Noise dots
        paint.strokeWidth = 2f
        repeat(30) {
            paint.color = Color.rgb(80 + rng.nextInt(150), 80 + rng.nextInt(150), 60 + rng.nextInt(150))
            canvas.drawPoint(rng.nextFloat() * w, rng.nextFloat() * h, paint)
        }
        // Characters with random tilt
        paint.textSize       = 26f
        paint.isFakeBoldText = true
        code.forEachIndexed { i, c ->
            paint.color = Color.rgb(20 + rng.nextInt(80), 20 + rng.nextInt(80), 60 + rng.nextInt(120))
            val cx = 16f + i * 28f; val cy = 32f
            val saved = canvas.save()
            canvas.rotate((rng.nextFloat() - 0.5f) * 24f, cx, cy)
            canvas.drawText(c.toString(), cx, cy, paint)
            canvas.restoreToCount(saved)
        }

        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        val bytes = baos.toByteArray()

        val resp = newFixedLengthResponse(Status.OK, "image/png",
                                          ByteArrayInputStream(bytes), bytes.size.toLong())
        resp.addHeader("X-Captcha-Id",  cid)
        resp.addHeader("Cache-Control", "no-store")
        return resp
    }

    private fun authRegister(session: IHTTPSession): Response {
        val body = readJsonBody(session) ?: return err(400, "Invalid JSON body")
        val username    = body.optString("username").trim()
        val password    = body.optString("password")
        val captchaId   = body.optString("captchaId")
        val captchaCode = body.optString("captchaCode")

        if (username.isEmpty() || password.isEmpty())
            return err(400, "username and password required")

        val cap = captchas[captchaId]
        if (cap == null || cap.expiresAt < System.currentTimeMillis())
            return err(400, "Captcha expired or invalid")
        if (!cap.code.equals(captchaCode, ignoreCase = true))
            return err(400, "Captcha code incorrect")
        captchas.remove(captchaId)

        val prefs = context.getSharedPreferences("xlocalhost_users", Context.MODE_PRIVATE)
        if (prefs.contains("user_$username")) return err(409, "Username already exists")

        prefs.edit()
            .putString("user_$username", """{"hash":"${hashPassword(password)}","role":"user"}""")
            .apply()
        return ok(JSONObject().put("message", "User registered successfully"))
    }

    private fun authLogin(session: IHTTPSession): Response {
        val body     = readJsonBody(session) ?: return err(400, "Invalid JSON body")
        val username = body.optString("username").trim()
        val password = body.optString("password")
        if (username.isEmpty() || password.isEmpty()) return err(400, "username and password required")

        val prefs    = context.getSharedPreferences("xlocalhost_users", Context.MODE_PRIVATE)
        val raw      = prefs.getString("user_$username", null) ?: return err(401, "Invalid credentials")
        val userJson = JSONObject(raw)
        if (userJson.optString("hash") != hashPassword(password)) return err(401, "Invalid credentials")

        val role  = userJson.optString("role", "user")
        val token = java.util.UUID.randomUUID().toString()
        sessions[token] = SessionEntry(username, role, System.currentTimeMillis() + SESSION_TTL)

        val resp = ok(JSONObject().put("message", "Login successful").put("username", username).put("role", role))
        resp.addHeader("Set-Cookie", "$COOKIE_NAME=$token; HttpOnly; Path=/; Max-Age=86400")
        return resp
    }

    private fun authLogout(session: IHTTPSession): Response {
        parseCookie(session, COOKIE_NAME)?.let { sessions.remove(it) }
        val resp = ok(JSONObject().put("message", "Logged out"))
        resp.addHeader("Set-Cookie", "$COOKIE_NAME=; HttpOnly; Path=/; Max-Age=0")
        return resp
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYSTEM API
    // ═══════════════════════════════════════════════════════════════════════════

    private fun systemStatus(session: IHTTPSession): Response {
        val scopes = session.parameters["scope"] ?: listOf("user", "filesystem", "database", "system")
        val result = JSONObject()

        if ("user" in scopes) {
            val entry = parseCookie(session, COOKIE_NAME)?.let { sessions[it] }
            result.put("user", if (entry != null)
                JSONObject().put("authenticated", true)
                            .put("username", entry.username)
                            .put("role", entry.role)
            else JSONObject().put("authenticated", false))
        }

        if ("filesystem" in scopes) {
            val root = DocumentFile.fromTreeUri(context, folderUri)
            result.put("filesystem", JSONObject()
                .put("available", root != null)
                .put("uri", folderUri.toString())
                .put("canWrite", root?.canWrite() ?: false))
        }

        if ("database" in scopes) {
            val dbs = context.filesDir.listFiles()
                ?.filter { it.extension == "db" || it.extension == "sqlite" }
                ?.map { it.name } ?: emptyList()
            result.put("database", JSONObject()
                .put("available", dbs.isNotEmpty())
                .put("files", JSONArray(dbs)))
        }

        if ("system" in scopes) {
            val rt = Runtime.getRuntime()
            result.put("system", JSONObject()
                .put("freeMemoryMb",      rt.freeMemory() / 1_048_576)
                .put("totalMemoryMb",     rt.totalMemory() / 1_048_576)
                .put("maxMemoryMb",       rt.maxMemory() / 1_048_576)
                .put("availableProcessors", rt.availableProcessors())
                .put("androidSdk",        Build.VERSION.SDK_INT)
                .put("serverTimeMs",      System.currentTimeMillis()))
        }

        return ok(result)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE API
    // ═══════════════════════════════════════════════════════════════════════════

    private fun resolveDoc(path: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        if (path.isEmpty() || path == "/") return root
        var cur: DocumentFile = root
        for (part in path.trimStart('/').split("/").filter { it.isNotEmpty() }) {
            cur = cur.findFile(part) ?: return null
        }
        return cur
    }

    /** Returns (parentDir, lastName) pair — parent must exist. */
    private fun resolveParentAndName(path: String): Pair<DocumentFile?, String> {
        val parts = path.trimStart('/').split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Pair(null, "")
        val root  = DocumentFile.fromTreeUri(context, folderUri) ?: return Pair(null, "")
        var cur: DocumentFile = root
        for (part in parts.dropLast(1)) {
            cur = cur.findFile(part) ?: return Pair(null, "")
        }
        return Pair(cur, parts.last())
    }

    // ── list ──────────────────────────────────────────────────────────────────
    private fun fsListDir(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: "/"
        val doc  = resolveDoc(path) ?: return err(404, "Path not found")
        if (!doc.isDirectory) return err(400, "Not a directory")

        val arr = JSONArray()
        doc.listFiles().forEach { f ->
            arr.put(JSONObject()
                .put("name",         f.name ?: "")
                .put("isDirectory",  f.isDirectory)
                .put("size",         if (f.isFile) f.length() else 0)
                .put("mimeType",     f.type ?: "")
                .put("lastModified", f.lastModified()))
        }
        return ok(JSONObject().put("path", path).put("entries", arr))
    }

    // ── download (with Range) ─────────────────────────────────────────────────
    private fun fsDownload(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: return err(400, "path required")
        val doc  = resolveDoc(path) ?: return err(404, "Not found")
        if (!doc.isFile) return err(400, "Not a file")
        return streamWithRange(session, doc)
    }

    private fun streamWithRange(session: IHTTPSession, doc: DocumentFile): Response {
        val mime      = doc.type?.takeIf { it.isNotEmpty() } ?: guessMime(doc.name ?: "")
        val totalSize = doc.length()
        val rangeHdr  = session.headers["range"]

        if (rangeHdr != null && rangeHdr.startsWith("bytes=")) {
            val spec = rangeHdr.removePrefix("bytes=")
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val startStr = spec.substring(0, dash).trim()
                val endStr   = spec.substring(dash + 1).trim()
                val start    = startStr.toLongOrNull() ?: 0L
                val end      = endStr.toLongOrNull() ?: (totalSize - 1)
                val last     = minOf(end, totalSize - 1)
                val length   = last - start + 1

                val inp = context.contentResolver.openInputStream(doc.uri)
                    ?: return err(500, "Cannot open file")
                if (start > 0) inp.skip(start)
                val resp = newFixedLengthResponse(Status.PARTIAL_CONTENT, mime,
                                                   LimitedInputStream(inp, length), length)
                resp.addHeader("Content-Range",  "bytes $start-$last/$totalSize")
                resp.addHeader("Accept-Ranges",  "bytes")
                resp.addHeader("Content-Disposition", "inline; filename=\"${doc.name}\"")
                return resp
            }
        }

        val inp  = context.contentResolver.openInputStream(doc.uri)
            ?: return err(500, "Cannot open file")
        val resp = newFixedLengthResponse(Status.OK, mime, inp, totalSize)
        resp.addHeader("Accept-Ranges",  "bytes")
        resp.addHeader("Content-Disposition", "attachment; filename=\"${doc.name}\"")
        return resp
    }

    // ── upload ────────────────────────────────────────────────────────────────
    private fun fsUpload(session: IHTTPSession): Response {
        if (!allowModification) return err(403, "File modification disabled")
        val path     = session.parameters["path"]?.firstOrNull() ?: "/"
        val destDir  = resolveDoc(path) ?: return err(404, "Destination not found")
        if (!destDir.isDirectory) return err(400, "Destination is not a directory")

        val tempFiles = mutableMapOf<String, String>()
        try { session.parseBody(tempFiles) } catch (e: Exception) {
            return err(400, "Body parse error: ${e.message}")
        }

        val uploaded   = JSONArray()
        val fileParams = session.parameters

        tempFiles.forEach { (partName, tmpPath) ->
            val originalName = fileParams["fileName"]?.firstOrNull()
                ?: fileParams["${partName}_name"]?.firstOrNull()
                ?: File(tmpPath).name
            val mime = guessMime(originalName)
            destDir.findFile(originalName)?.delete()
            val newDoc = destDir.createFile(mime, originalName) ?: return@forEach
            File(tmpPath).inputStream().use { inp ->
                context.contentResolver.openOutputStream(newDoc.uri)?.use { out -> inp.copyTo(out) }
            }
            uploaded.put(originalName)
        }

        (session.parameters["emptyDirs[]"] ?: emptyList()).forEach { d ->
            val trimmed = d.trim()
            if (trimmed.isNotEmpty()) { destDir.createDirectory(trimmed); uploaded.put("$trimmed/") }
        }

        return ok(JSONObject().put("uploaded", uploaded).put("count", uploaded.length()))
    }

    // ── delete ────────────────────────────────────────────────────────────────
    private fun fsDelete(session: IHTTPSession): Response {
        if (!allowModification) return err(403, "File modification disabled")
        val path = session.parameters["path"]?.firstOrNull() ?: return err(400, "path required")
        val doc  = resolveDoc(path) ?: return err(404, "Not found")
        return if (doc.delete()) ok(JSONObject().put("deleted", path))
               else err(500, "Could not delete")
    }

    // ── rename ────────────────────────────────────────────────────────────────
    private fun fsRename(session: IHTTPSession): Response {
        if (!allowModification) return err(403, "File modification disabled")
        val body    = readJsonBody(session) ?: return err(400, "Invalid JSON body")
        val path    = body.optString("path").takeIf { it.isNotEmpty() } ?: return err(400, "path required")
        val newName = body.optString("newName").takeIf { it.isNotEmpty() } ?: return err(400, "newName required")
        val doc     = resolveDoc(path) ?: return err(404, "Not found")
        return try {
            if (doc.renameTo(newName) != null) ok(JSONObject().put("renamed", newName))
            else err(500, "Rename failed")
        } catch (e: Exception) { err(500, "Rename error: ${e.message}") }
    }

    // ── mkdir ─────────────────────────────────────────────────────────────────
    private fun fsMkdir(session: IHTTPSession): Response {
        if (!allowModification) return err(403, "File modification disabled")
        val body    = readJsonBody(session) ?: return err(400, "Invalid JSON body")
        val path    = body.optString("path").takeIf { it.isNotEmpty() } ?: return err(400, "path required")
        val (parent, name) = resolveParentAndName(path)
        if (parent == null) return err(404, "Parent directory not found")
        if (name.isEmpty()) return err(400, "Directory name required")
        return if (parent.createDirectory(name) != null) ok(JSONObject().put("created", path))
               else err(500, "Could not create directory")
    }

    // ── stat ──────────────────────────────────────────────────────────────────
    private fun fsStat(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: return err(400, "path required")
        val doc  = resolveDoc(path) ?: return err(404, "Not found")
        return ok(JSONObject()
            .put("name",         doc.name ?: "")
            .put("path",         path)
            .put("isDirectory",  doc.isDirectory)
            .put("isFile",       doc.isFile)
            .put("size",         if (doc.isFile) doc.length() else 0)
            .put("mimeType",     doc.type ?: "")
            .put("canRead",      doc.canRead())
            .put("canWrite",     doc.canWrite())
            .put("lastModified", doc.lastModified()))
    }

    // ── move ──────────────────────────────────────────────────────────────────
    private fun fsMove(session: IHTTPSession): Response {
        if (!allowModification) return err(403, "File modification disabled")
        val body   = readJsonBody(session) ?: return err(400, "Invalid JSON body")
        val src    = body.optString("src").takeIf { it.isNotEmpty() } ?: return err(400, "src required")
        val dst    = body.optString("dst").takeIf { it.isNotEmpty() } ?: return err(400, "dst required")
        val srcDoc = resolveDoc(src) ?: return err(404, "Source not found")
        val dstDir = resolveDoc(dst) ?: return err(404, "Destination directory not found")
        if (!dstDir.isDirectory) return err(400, "Destination is not a directory")
        val name   = srcDoc.name ?: return err(500, "Source has no name")
        return try {
            val mime    = srcDoc.type ?: guessMime(name)
            val dstFile = dstDir.createFile(mime, name) ?: return err(500, "Cannot create at destination")
            context.contentResolver.openInputStream(srcDoc.uri)?.use { inp ->
                context.contentResolver.openOutputStream(dstFile.uri)?.use { out -> inp.copyTo(out) }
            }
            srcDoc.delete()
            ok(JSONObject().put("moved", "$dst/$name"))
        } catch (e: Exception) { err(500, "Move failed: ${e.message}") }
    }

    // ── thumbnail (WebP) ──────────────────────────────────────────────────────
    private fun fsThumbnail(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull() ?: return err(400, "path required")
        val size = session.parameters["size"]?.firstOrNull()?.toIntOrNull() ?: 256
        val doc  = resolveDoc(path) ?: return err(404, "Not found")
        if (!doc.isFile) return err(400, "Not a file")
        return try {
            val inp = context.contentResolver.openInputStream(doc.uri)
                ?: return err(500, "Cannot open file")
            val original = BitmapFactory.decodeStream(inp) ?: return err(415, "Cannot decode image")
            val scale    = minOf(size.toFloat() / original.width, size.toFloat() / original.height)
            val dstW     = (original.width  * scale).toInt().coerceAtLeast(1)
            val dstH     = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled   = Bitmap.createScaledBitmap(original, dstW, dstH, true)
            original.recycle()
            val baos = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            scaled.compress(fmt, 80, baos)
            scaled.recycle()
            val bytes = baos.toByteArray()
            newFixedLengthResponse(Status.OK, "image/webp",
                                   ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: Exception) { err(500, "Thumbnail error: ${e.message}") }
    }

    // ── zip (STORED + CRC32) ──────────────────────────────────────────────────
    private fun fsZip(session: IHTTPSession): Response {
        val paths = session.parameters["path[]"]
            ?: session.parameters["path"]
            ?: return err(400, "path required")

        val baos = ByteArrayOutputStream()
        val zos  = ZipOutputStream(baos)
        zos.setMethod(ZipOutputStream.STORED)

        paths.forEach { p ->
            val doc = resolveDoc(p) ?: return@forEach
            addZipEntry(zos, doc, doc.name ?: "file")
        }
        zos.finish(); zos.close()
        val bytes = baos.toByteArray()

        val resp = newFixedLengthResponse(Status.OK, "application/zip",
                                          ByteArrayInputStream(bytes), bytes.size.toLong())
        resp.addHeader("Content-Disposition", "attachment; filename=\"archive.zip\"")
        return resp
    }

    private fun addZipEntry(zos: ZipOutputStream, doc: DocumentFile, entryName: String) {
        if (doc.isDirectory) {
            val children = doc.listFiles()
            if (children.isEmpty()) {
                val e = ZipEntry("$entryName/").apply { method = ZipEntry.STORED; size = 0; compressedSize = 0; crc = 0 }
                zos.putNextEntry(e); zos.closeEntry()
            } else {
                children.forEach { child -> addZipEntry(zos, child, "$entryName/${child.name ?: "file"}") }
            }
        } else {
            val data = context.contentResolver.openInputStream(doc.uri)?.readBytes() ?: return
            val crc  = CRC32().also { it.update(data) }
            val e    = ZipEntry(entryName).apply {
                method         = ZipEntry.STORED
                size           = data.size.toLong()
                compressedSize = data.size.toLong()
                this.crc       = crc.value
            }
            zos.putNextEntry(e); zos.write(data); zos.closeEntry()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATABASE API
    // ═══════════════════════════════════════════════════════════════════════════

    private fun openDb(session: IHTTPSession): SQLiteDatabase? {
        val dbName = session.parameters["db"]?.firstOrNull() ?: return null
        val safe   = dbName.replace("..", "").replace("/", "").replace("\\", "")
        val file   = File(context.filesDir, safe)
        if (!file.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) { null }
    }

    // ── schema ────────────────────────────────────────────────────────────────
    private fun dbSchema(session: IHTTPSession): Response {
        val db = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            val tables = JSONArray()
            db.rawQuery("SELECT name,type FROM sqlite_master WHERE type IN ('table','view') ORDER BY name", null).use { c ->
                while (c.moveToNext()) {
                    val tblName = c.getString(0)
                    val cols    = JSONArray()
                    db.rawQuery("PRAGMA table_info(\"$tblName\")", null).use { p ->
                        while (p.moveToNext()) {
                            cols.put(JSONObject()
                                .put("cid",          p.getInt(0))
                                .put("name",         p.getString(1))
                                .put("type",         p.getString(2))
                                .put("notnull",      p.getInt(3) == 1)
                                .put("defaultValue", if (p.isNull(4)) JSONObject.NULL else p.getString(4))
                                .put("pk",           p.getInt(5) == 1))
                        }
                    }
                    tables.put(JSONObject().put("name", tblName).put("type", c.getString(1)).put("columns", cols))
                }
            }
            ok(JSONObject().put("tables", tables))
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ── DB route dispatch ─────────────────────────────────────────────────────
    private fun dbRoute(session: IHTTPSession, method: Method, uri: String): Response {
        val parts = uri.removePrefix("/api/db/").split("/").filter { it.isNotEmpty() }
        return when {
            parts.size == 1 -> {
                val table = parts[0]
                when (method) {
                    Method.GET  -> dbTable(session, table)
                    Method.POST -> dbInsert(session, table)
                    else        -> err(405, "Method not allowed")
                }
            }
            parts.size >= 2 -> {
                val table = parts[0]; val rowId = parts[1]
                if (parts.getOrNull(2) == "cell-data") return dbCellData(session, table, rowId)
                when (method) {
                    Method.GET    -> dbCellData(session, table, rowId)
                    Method.PUT    -> dbUpdate(session, table, rowId)
                    Method.DELETE -> dbDelete(session, table, rowId)
                    else          -> err(405, "Method not allowed")
                }
            }
            else -> err(404, "Unknown DB route")
        }
    }

    // ── table query (with filters + pagination) ───────────────────────────────
    private fun dbTable(session: IHTTPSession, table: String): Response {
        val db = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            val params        = session.parameters
            val page          = params["page"]?.firstOrNull()?.toIntOrNull() ?: 1
            val pageSize      = params["pageSize"]?.firstOrNull()?.toIntOrNull() ?: 50
            val rowsAsObjects = params["rowsAsObjects"]?.firstOrNull()?.toBoolean() ?: false
            val offset        = (page - 1) * pageSize
            val tbl           = table.replace("\"", "")

            val whereClauses = mutableListOf<String>()
            val whereArgs    = mutableListOf<String>()
            for ((k, vs) in params) {
                if (k.startsWith("filter[") && k.endsWith("]")) {
                    val col  = k.removePrefix("filter[").removeSuffix("]")
                    val expr = vs.firstOrNull() ?: continue
                    when {
                        expr.startsWith("∈") -> {
                            val items = expr.removePrefix("∈").split(",")
                            whereClauses.add("\"$col\" IN (${items.joinToString(",") { "?" }})")
                            whereArgs.addAll(items)
                        }
                        expr.startsWith("∉") -> {
                            val items = expr.removePrefix("∉").split(",")
                            whereClauses.add("\"$col\" NOT IN (${items.joinToString(",") { "?" }})")
                            whereArgs.addAll(items)
                        }
                        else -> { whereClauses.add("\"$col\" = ?"); whereArgs.add(expr) }
                    }
                }
            }

            val where = if (whereClauses.isEmpty()) "" else "WHERE ${whereClauses.joinToString(" AND ")}"
            val total = db.rawQuery("SELECT COUNT(*) FROM \"$tbl\" $where", whereArgs.toTypedArray()).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
            val allArgs = (whereArgs + listOf(pageSize.toString(), offset.toString())).toTypedArray()
            val cursor  = db.rawQuery("SELECT * FROM \"$tbl\" $where LIMIT ? OFFSET ?", allArgs)

            val colNames = cursor.columnNames
            val colArr   = if (rowsAsObjects) JSONArray() else JSONArray(colNames.toList())
            val rows     = JSONArray()

            cursor.use { c ->
                while (c.moveToNext()) {
                    if (rowsAsObjects) {
                        val obj = JSONObject()
                        colNames.forEachIndexed { i, n -> obj.put(n, if (c.isNull(i)) JSONObject.NULL else c.getString(i)) }
                        rows.put(obj)
                    } else {
                        val arr = JSONArray()
                        colNames.indices.forEach { i -> arr.put(if (c.isNull(i)) JSONObject.NULL else c.getString(i)) }
                        rows.put(arr)
                    }
                }
            }
            ok(JSONObject()
                .put("table",    table)
                .put("columns",  colArr)
                .put("rows",     rows)
                .put("total",    total)
                .put("page",     page)
                .put("pageSize", pageSize))
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ── insert ────────────────────────────────────────────────────────────────
    private fun dbInsert(session: IHTTPSession, table: String): Response {
        val db   = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            val body = readJsonBody(session) ?: return err(400, "Invalid JSON body")
            val tbl  = table.replace("\"", "")
            val keys = body.keys().asSequence().toList()
            db.execSQL(
                "INSERT INTO \"$tbl\" (${keys.joinToString(",") { "\"$it\"" }}) VALUES (${keys.joinToString(",") { "?" }})",
                keys.map { body.optString(it) }.toTypedArray()
            )
            val lastId = db.rawQuery("SELECT last_insert_rowid()", null).use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            }
            ok(JSONObject().put("inserted", true).put("rowId", lastId))
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ── update ────────────────────────────────────────────────────────────────
    private fun dbUpdate(session: IHTTPSession, table: String, rowId: String): Response {
        val db   = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            val body = readJsonBody(session) ?: return err(400, "Invalid JSON body")
            val tbl  = table.replace("\"", "")
            val keys = body.keys().asSequence().toList()
            db.execSQL(
                "UPDATE \"$tbl\" SET ${keys.joinToString(",") { "\"$it\" = ?" }} WHERE rowid = ?",
                (keys.map { body.optString(it) } + listOf(rowId)).toTypedArray()
            )
            ok(JSONObject().put("updated", true).put("rowId", rowId))
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ── delete ────────────────────────────────────────────────────────────────
    private fun dbDelete(session: IHTTPSession, table: String, rowId: String): Response {
        val db = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            db.execSQL("DELETE FROM \"${table.replace("\"", "")}\" WHERE rowid = ?", arrayOf(rowId))
            ok(JSONObject().put("deleted", true).put("rowId", rowId))
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ── cell-data ─────────────────────────────────────────────────────────────
    private fun dbCellData(session: IHTTPSession, table: String, rowId: String): Response {
        val db  = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            val col = session.parameters["col"]?.firstOrNull() ?: return err(400, "col required")
            val tbl = table.replace("\"", "")
            db.rawQuery("SELECT \"$col\" FROM \"$tbl\" WHERE rowid = ?", arrayOf(rowId)).use { c ->
                if (!c.moveToFirst()) return err(404, "Row not found")
                val bytes = c.getBlob(0) ?: return err(404, "Cell is null")
                newFixedLengthResponse(Status.OK, "application/octet-stream",
                                       ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ── raw query ─────────────────────────────────────────────────────────────
    private fun dbQuery(session: IHTTPSession): Response {
        val db   = openDb(session) ?: return err(400, "db parameter required or database not found")
        return try {
            val body = readJsonBody(session) ?: return err(400, "Invalid JSON body")
            val sql  = body.optString("sql").takeIf { it.isNotEmpty() } ?: return err(400, "sql required")
            val args = body.optJSONArray("args")?.let { a -> Array(a.length()) { a.optString(it) } }
            val isSelect = sql.trim().uppercase().let { it.startsWith("SELECT") || it.startsWith("PRAGMA") }

            if (isSelect) {
                val c    = if (args != null) db.rawQuery(sql, args) else db.rawQuery(sql, null)
                val cols = JSONArray(c.columnNames.toList())
                val rows = JSONArray()
                c.use { cur ->
                    while (cur.moveToNext()) {
                        val row = JSONArray()
                        cur.columnNames.indices.forEach { i -> row.put(if (cur.isNull(i)) JSONObject.NULL else cur.getString(i)) }
                        rows.put(row)
                    }
                }
                ok(JSONObject().put("columns", cols).put("rows", rows).put("rowCount", rows.length()))
            } else {
                if (args != null) db.execSQL(sql, args) else db.execSQL(sql)
                val changes = db.rawQuery("SELECT changes()", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                ok(JSONObject().put("rowsAffected", changes))
            }
        } catch (e: SQLiteException) { errSql(e) } finally { db.close() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATIC / LEGACY HTML FILE BROWSER
    // ═══════════════════════════════════════════════════════════════════════════

    private fun serveStatic(session: IHTTPSession): Response {
        val m    = session.method
        val uri  = session.uri
        val path = session.uri.trimStart('/').trimEnd('/')

        // Serve combined_styles.css
        if (uri == "/combined_styles.css") {
            val cssStream = context.assets.open("combined_styles.css")
            return newFixedLengthResponse(Status.OK, "text/css", cssStream, cssStream.available().toLong())
        }

        val root = DocumentFile.fromTreeUri(context, folderUri)
            ?: return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot access root folder")

        if (m == Method.PUT || m == Method.DELETE || m == Method.POST) {
            if (!allowModification)
                return newFixedLengthResponse(Status.FORBIDDEN, MIME_PLAINTEXT, "File modification disabled")
            return legacyModify(session, root, path)
        }

        if (path.isEmpty()) return htmlDirListing(root, "/")

        var cur: DocumentFile = root
        for (part in path.split("/").filter { it.isNotEmpty() }) {
            cur = cur.findFile(part)
                ?: return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found: /$path")
        }
        return if (cur.isDirectory) htmlDirListing(cur, "/$path") else streamWithRange(session, cur)
    }

    private fun legacyModify(session: IHTTPSession, root: DocumentFile, path: String): Response {
        return when (session.method) {
            Method.DELETE -> {
                val parts = path.split("/").filter { it.isNotEmpty() }
                var cur   = root
                for (p in parts.dropLast(1)) {
                    cur = cur.findFile(p) ?: return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }
                val target = cur.findFile(parts.last()) ?: return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                if (target.delete()) newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, "Deleted")
                else newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not delete")
            }
            Method.PUT -> {
                val parts = path.split("/").filter { it.isNotEmpty() }
                if (parts.isEmpty()) return newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid path")
                var dir = root
                for (p in parts.dropLast(1)) {
                    dir = dir.findFile(p) ?: dir.createDirectory(p)
                        ?: return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot create dir")
                }
                val name = parts.last()
                dir.findFile(name)?.delete()
                val newFile = dir.createFile(guessMime(name), name)
                    ?: return newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Cannot create file")
                context.contentResolver.openOutputStream(newFile.uri)?.use { out -> session.inputStream.copyTo(out) }
                newFixedLengthResponse(Status.CREATED, MIME_PLAINTEXT, "Created")
            }
            else -> newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not allowed")
        }
    }

    private fun htmlDirListing(dir: DocumentFile, path: String): Response {
        val sorted = dir.listFiles().sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" }
        )
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        sb.append("<title>x-localhost — $path</title>")
        sb.append("<link rel=\"stylesheet\" href=\"/combined_styles.css\">\n")
        sb.append("</head><body>")
        sb.append("<h2>&#128193; $path</h2>")
        if (path != "/") {
            val parent = if (path.count { it == '/' } == 1) "/" else path.substringBeforeLast("/")
            sb.append("<a href=\"$parent\" class=\"dir\"><span>&#8593;</span><span>[Parent directory]</span></a>")
        }
        sorted.forEach { f ->
            val name = f.name ?: return@forEach
            val href = if (path == "/") "/$name" else "$path/$name"
            val disp = if (f.isDirectory) "$href/" else href
            sb.append("<a href=\"$disp\" class=\"${if (f.isDirectory) "dir" else ""}\">")
            sb.append("<span>${if (f.isDirectory) "&#128193;" else "&#128196;"}</span>")
            sb.append("<span>$name</span>")
            sb.append("<span class=\"meta\">${if (!f.isDirectory) formatSize(f.length()) else "DIR"}</span></a>")
        }
        sb.append("<footer>x-localhost &bull; ${sorted.size} entries</footer></body></html>")
        return newFixedLengthResponse(Status.OK, "text/html; charset=UTF-8", sb.toString())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expiresAt < now }
        captchas.entries.removeIf { it.value.expiresAt < now }
    }

    private fun parseCookie(session: IHTTPSession, name: String): String? =
        session.headers["cookie"]?.split(";")?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }?.removePrefix("$name=")

    private fun readJsonBody(session: IHTTPSession): JSONObject? {
        return try {
            val len = session.headers["content-length"]?.toIntOrNull() ?: return null
            if (len <= 0) return null
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val n = session.inputStream.read(buf, off, len - off)
                if (n == -1) break
                off += n
            }
            JSONObject(String(buf, Charsets.UTF_8))
        } catch (_: Exception) { null }
    }

    private fun ok(body: JSONObject): Response {
        body.put("ok", true)
        return newFixedLengthResponse(Status.OK, "application/json; charset=UTF-8", body.toString())
    }

    private fun err(code: Int, message: String): Response {
        val status: Response.IStatus = when (code) {
            400  -> Status.BAD_REQUEST
            401  -> Status.UNAUTHORIZED
            403  -> Status.FORBIDDEN
            404  -> Status.NOT_FOUND
            405  -> Status.METHOD_NOT_ALLOWED
            409  -> Status.CONFLICT
            415  -> STATUS_415
            420  -> STATUS_420
            else -> Status.INTERNAL_ERROR
        }
        val body = JSONObject().put("ok", false).put("error", message).put("code", code)
        return newFixedLengthResponse(status, "application/json; charset=UTF-8", body.toString())
    }

    private fun errSql(e: SQLiteException): Response = err(420, "SQL error: ${e.message}")

    private fun formatSize(bytes: Long): String = when {
        bytes < 1_024         -> "${bytes}B"
        bytes < 1_048_576     -> "${"%.1f".format(bytes / 1_024.0)}KB"
        bytes < 1_073_741_824 -> "${"%.1f".format(bytes / 1_048_576.0)}MB"
        else                  -> "${"%.2f".format(bytes / 1_073_741_824.0)}GB"
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty() || ext == name.lowercase()) return "application/octet-stream"
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (ext) {
            "html","htm"  -> "text/html"
            "css"         -> "text/css"
            "js","mjs"    -> "application/javascript"
            "ts"          -> "application/typescript"
            "json"        -> "application/json"
            "xml"         -> "application/xml"
            "txt","log","md","ini","env" -> "text/plain"
            "yaml","yml"  -> "application/x-yaml"
            "wasm"        -> "application/wasm"
            "avif"        -> "image/avif"
            "heic","heif" -> "image/heic"
            "flac"        -> "audio/flac"
            "opus"        -> "audio/opus"
            "weba"        -> "audio/webm"
            "mkv"         -> "video/x-matroska"
            "flv"         -> "video/x-flv"
            "wmv"         -> "video/x-ms-wmv"
            "woff"        -> "font/woff"
            "woff2"       -> "font/woff2"
            "ttf"         -> "font/ttf"
            "otf"         -> "font/otf"
            "eot"         -> "application/vnd.ms-fontobject"
            "docx"        -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx"        -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx"        -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "epub"        -> "application/epub+zip"
            "rar"         -> "application/vnd.rar"
            "7z"          -> "application/x-7z-compressed"
            "apk"         -> "application/vnd.android.package-archive"
            "iso"         -> "application/x-iso9660-image"
            "sqlite","db" -> "application/vnd.sqlite3"
            "sql"         -> "application/sql"
            "ics"         -> "text/calendar"
            "kt","java","c","cpp","h","cs","py","sh","bat","rs","go","swift" -> "text/plain"
            else          -> "application/octet-stream"
        }
    }

    // ── LimitedInputStream ────────────────────────────────────────────────────
    private class LimitedInputStream(
        private val src: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            return src.read().also { if (it != -1) remaining-- }
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = src.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n != -1) remaining -= n
            return n
        }
        override fun close() = src.close()
    }
}
