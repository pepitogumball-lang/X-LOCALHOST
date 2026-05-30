package com.flinger.localserver

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.io.InputStream

class LocalWebServer(
    private val context: Context,
    private val folderUri: Uri,
    port: Int = 8080,
    private val allowModification: Boolean = false,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val rawUri = session.uri
        val requestPath = rawUri.trimStart('/').removeSuffix("/")

        val rootDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Error: no se puede acceder a la carpeta raíz."
            )

        if (method == Method.PUT || method == Method.DELETE || method == Method.POST) {
            if (!allowModification) {
                return newFixedLengthResponse(
                    Response.Status.FORBIDDEN, MIME_PLAINTEXT,
                    "Modificación de archivos deshabilitada."
                )
            }
            return handleModification(session, rootDoc, requestPath)
        }

        if (requestPath.isEmpty()) {
            return buildDirectoryListing(rootDoc, "/")
        }

        val parts = requestPath.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = rootDoc
        for (part in parts) {
            current = current.findFile(part)
                ?: return newFixedLengthResponse(
                    Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                    "404 No encontrado: /$requestPath"
                )
        }

        return if (current.isDirectory) {
            buildDirectoryListing(current, "/$requestPath")
        } else {
            serveFileContent(current)
        }
    }

    private fun handleModification(
        session: IHTTPSession,
        root: DocumentFile,
        path: String
    ): Response {
        return when (session.method) {
            Method.DELETE -> {
                val parts = path.split("/").filter { it.isNotEmpty() }
                var current: DocumentFile = root
                for (part in parts.dropLast(1)) {
                    current = current.findFile(part)
                        ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No encontrado")
                }
                val target = current.findFile(parts.last())
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No encontrado")
                if (target.delete()) {
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Eliminado.")
                } else {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No se pudo eliminar.")
                }
            }
            Method.PUT -> {
                val parts = path.split("/").filter { it.isNotEmpty() }
                if (parts.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Ruta inválida.")
                }
                var dir: DocumentFile = root
                for (part in parts.dropLast(1)) {
                    dir = dir.findFile(part) ?: dir.createDirectory(part)
                        ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No se pudo crear el directorio.")
                }
                val fileName = parts.last()
                val mime = guessMimeFromName(fileName)
                val existing = dir.findFile(fileName)
                existing?.delete()
                val newFile = dir.createFile(mime, fileName)
                    ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No se pudo crear el archivo.")
                val body = session.inputStream
                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                    body.copyTo(out)
                }
                newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "Creado: /$path")
            }
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Método no soportado.")
        }
    }

    private fun buildDirectoryListing(dir: DocumentFile, path: String): Response {
        val entries = dir.listFiles()
        val sorted = entries.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }
                .thenBy { it.name?.lowercase() ?: "" }
        )

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"es\">")
        sb.append("<head><meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.append("<title>x-localhost — $path</title>")
        sb.append("<style>")
        sb.append("*{box-sizing:border-box;margin:0;padding:0}")
        sb.append("body{font-family:'Courier New',monospace;background:#0d1117;color:#c9d1d9;padding:24px;min-height:100vh}")
        sb.append("h2{color:#58a6ff;border-bottom:1px solid #21262d;padding-bottom:12px;margin-bottom:16px;font-size:1.1rem}")
        sb.append("a{display:flex;align-items:center;gap:8px;padding:8px 12px;text-decoration:none;color:#c9d1d9;border-radius:6px;transition:background .15s}")
        sb.append("a:hover{background:#161b22}")
        sb.append(".dir{color:#58a6ff}")
        sb.append(".meta{margin-left:auto;color:#6e7681;font-size:.8rem;white-space:nowrap}")
        sb.append("footer{margin-top:32px;color:#6e7681;font-size:.75rem;border-top:1px solid #21262d;padding-top:12px}")
        sb.append("</style></head><body>")
        sb.append("<h2>&#128193; $path</h2>")

        if (path != "/") {
            val parent = if (path.count { it == '/' } == 1) "/" else path.substringBeforeLast("/") + "/"
            sb.append("<a href=\"$parent\" class=\"dir\"><span>&#8593;</span><span>[Directorio padre]</span></a>")
        }

        for (file in sorted) {
            val name = file.name ?: continue
            val href = if (path == "/") "/$name" else "$path/$name"
            val displayHref = if (file.isDirectory) "$href/" else href
            val icon = if (file.isDirectory) "&#128193;" else "&#128196;"
            val cssClass = if (file.isDirectory) "dir" else ""
            val sizeStr = if (!file.isDirectory) formatSize(file.length()) else "DIR"
            sb.append("<a href=\"$displayHref\" class=\"$cssClass\">")
            sb.append("<span>$icon</span><span>$name</span>")
            sb.append("<span class=\"meta\">$sizeStr</span>")
            sb.append("</a>")
        }

        sb.append("<footer>x-localhost &bull; ${sorted.size} entradas</footer>")
        sb.append("</body></html>")

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", sb.toString())
    }

    private fun serveFileContent(file: DocumentFile): Response {
        val mimeType = file.type?.takeIf { it.isNotEmpty() } ?: guessMimeFromName(file.name ?: "")
        val inputStream: InputStream = try {
            context.contentResolver.openInputStream(file.uri)
                ?: return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "No se puede abrir el archivo."
                )
        } catch (e: IOException) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error al leer: ${e.message}"
            )
        }
        return newChunkedResponse(Response.Status.OK, mimeType, inputStream)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))}GB"
    }

    private fun guessMimeFromName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty() || extension == name.lowercase()) return "application/octet-stream"

        val fromAndroid = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (fromAndroid != null) return fromAndroid

        return when (extension) {
            "html", "htm", "shtml" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "ts" -> "application/typescript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "txt", "log", "ini", "env" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "yaml", "yml" -> "application/x-yaml"
            "wasm" -> "application/wasm"
            "avif" -> "image/avif"
            "heic", "heif" -> "image/heic"
            "psd" -> "image/vnd.adobe.photoshop"
            "flac" -> "audio/flac"
            "opus" -> "audio/opus"
            "weba" -> "audio/webm"
            "mkv" -> "video/x-matroska"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "epub" -> "application/epub+zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "apk" -> "application/vnd.android.package-archive"
            "iso" -> "application/x-iso9660-image"
            "sqlite", "db" -> "application/vnd.sqlite3"
            "sql" -> "application/sql"
            "ics" -> "text/calendar"
            "kt", "java", "c", "cpp", "h", "cs", "py", "sh", "bat", "rs", "go", "swift" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
