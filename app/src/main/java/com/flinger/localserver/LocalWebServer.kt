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
    private val folderUri: Uri
) : NanoHTTPD(8080) {

    override fun serve(session: IHTTPSession): Response {
        val rawUri = session.uri
        val requestPath = rawUri.trimStart('/').removeSuffix("/")

        val rootDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: No se puede acceder a la carpeta seleccionada."
            )

        if (requestPath.isEmpty()) {
            return buildDirectoryListing(rootDoc, "/")
        }

        val parts = requestPath.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = rootDoc

        for (part in parts) {
            val child = current.findFile(part)
                ?: return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "404 No encontrado: /$requestPath"
                )
            current = child
        }

        return if (current.isDirectory) {
            buildDirectoryListing(current, "/$requestPath")
        } else {
            serveFileContent(current)
        }
    }

    private fun buildDirectoryListing(dir: DocumentFile, path: String): Response {
        val entries = dir.listFiles()
        val sorted = entries.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() ?: "" }
        )

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>")
        sb.append("<html lang=\"es\">")
        sb.append("<head>")
        sb.append("<meta charset=\"UTF-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.append("<title>Índice de $path</title>")
        sb.append("<style>")
        sb.append("body{font-family:monospace;background:#1e1e2e;color:#cdd6f4;margin:0;padding:20px}")
        sb.append("h2{color:#89b4fa;border-bottom:1px solid #313244;padding-bottom:8px}")
        sb.append("a{display:block;padding:6px 8px;text-decoration:none;color:#cba6f7;border-radius:4px}")
        sb.append("a:hover{background:#313244}")
        sb.append(".dir{color:#89b4fa}")
        sb.append(".file{color:#cdd6f4}")
        sb.append(".size{color:#6c7086;font-size:0.85em;margin-left:8px}")
        sb.append("hr{border:none;border-top:1px solid #313244;margin:12px 0}")
        sb.append("</style>")
        sb.append("</head>")
        sb.append("<body>")
        sb.append("<h2>&#128193; Índice de $path</h2>")
        sb.append("<hr>")

        if (path != "/") {
            val parentPath = if (path.count { it == '/' } == 1) "/" else path.substringBeforeLast("/") + "/"
            sb.append("<a href=\"$parentPath\" class=\"dir\">&#8593; [Directorio padre]</a>")
        }

        for (file in sorted) {
            val name = file.name ?: continue
            val href = if (path == "/") "/$name" else "$path/$name"
            val displayHref = if (file.isDirectory) "$href/" else href
            val icon = if (file.isDirectory) "&#128193;" else "&#128196;"
            val cssClass = if (file.isDirectory) "dir" else "file"
            val sizeStr = if (!file.isDirectory) {
                val bytes = file.length()
                when {
                    bytes < 1024 -> "${bytes}B"
                    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                    else -> "${bytes / (1024 * 1024)}MB"
                }
            } else ""
            sb.append("<a href=\"$displayHref\" class=\"$cssClass\">$icon $name<span class=\"size\">$sizeStr</span></a>")
        }

        sb.append("<hr>")
        sb.append("<small style=\"color:#6c7086\">Flinger Local Server &bull; Puerto 8080</small>")
        sb.append("</body></html>")

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", sb.toString())
    }

    private fun serveFileContent(file: DocumentFile): Response {
        val mimeType = file.type?.takeIf { it.isNotEmpty() } ?: guessMimeFromName(file.name ?: "")
        val inputStream: InputStream = try {
            context.contentResolver.openInputStream(file.uri)
                ?: return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "No se puede abrir el archivo."
                )
        } catch (e: IOException) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error al leer el archivo: ${e.message}"
            )
        }
        return newChunkedResponse(Response.Status.OK, mimeType, inputStream)
    }

    private fun guessMimeFromName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()

        if (extension.isEmpty() || extension == name.lowercase()) {
            return "application/octet-stream"
        }

        val mimeTypeFromAndroid = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (mimeTypeFromAndroid != null) {
            return mimeTypeFromAndroid
        }

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
            "png" -> "image/png"
            "jpg", "jpeg", "jpe" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg", "svgz" -> "image/svg+xml"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            "heic", "heif" -> "image/heic"
            "ico" -> "image/x-icon"
            "bmp" -> "image/bmp"
            "tiff", "tif" -> "image/tiff"
            "psd" -> "image/vnd.adobe.photoshop"
            "mp3" -> "audio/mpeg"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "weba" -> "audio/webm"
            "opus" -> "audio/opus"
            "mid", "midi" -> "audio/midi"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "3gp" -> "video/3gpp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "rtf" -> "application/rtf"
            "epub" -> "application/epub+zip"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz", "gzip" -> "application/gzip"
            "bz2" -> "application/x-bzip2"
            "apk" -> "application/vnd.android.package-archive"
            "iso" -> "application/x-iso9660-image"
            "kt", "java", "c", "cpp", "h", "cs", "py", "sh", "bat" -> "text/plain"
            "sql" -> "application/sql"
            "sqlite", "db" -> "application/vnd.sqlite3"
            "ics" -> "text/calendar"
            else -> "application/octet-stream"
        }
    }
}
