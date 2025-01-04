package xyz.epicebic.spigotbuilder.util

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.io.path.readLines

class MapUtil {
    private val header: MutableList<String> = ArrayList()
    private val obf2Buk: BiMap<String, String> = HashBiMap.create()
    private val moj2Obf: BiMap<String, String> = HashBiMap.create()

    fun loadBuk(bukClasses: File) {
        for (line in Files.readAllLines(bukClasses.toPath())) {
            if (line.startsWith("#")) {
                header.add(line)
                continue
            }

            val split = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (split.size == 2) {
                obf2Buk[split[0]] = split[1]
            }
        }
    }

    fun makeFieldMaps(mojIn: File, fields: File, includeMethods: Boolean) {
        if (includeMethods) {
            for (line in Files.readAllLines(mojIn.toPath())) {
                if (line.startsWith("#")) {
                    continue
                }

                if (line.endsWith(":")) {
                    val parts = line.split(" -> ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val orig = parts[0].replace('.', '/')
                    val obf = parts[1].substring(0, parts[1].length - 1).replace('.', '/')

                    moj2Obf[orig] = obf
                }
            }
        }

        val outFields: MutableList<String> = ArrayList(header)

        var currentClass: String? = null
        outer@ for (line in Files.readAllLines(mojIn.toPath())) {
            var line = line
            if (line.startsWith("#")) {
                continue
            }
            line = line.trim { it <= ' ' }

            if (line.endsWith(":")) {
                currentClass = null

                val parts = line.split(" -> ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val orig = parts[0].replace('.', '/')
                val obf = parts[1].substring(0, parts[1].length - 1).replace('.', '/')

                val buk = deobfClass(obf, obf2Buk) ?: continue

                currentClass = buk
            } else if (currentClass != null) {
                val matcher = MEMBER_PATTERN.matcher(line)
                matcher.find()

                var obf = matcher.group(3)
                val nameDesc = matcher.group(2)
                if (!nameDesc.contains("(")) {
                    if (nameDesc == obf || nameDesc.contains("$")) {
                        continue
                    }
                    if (!includeMethods && (obf == "if" || obf == "do")) {
                        obf += "_"
                    }

                    outFields.add("$currentClass $obf $nameDesc")
                } else if (includeMethods) {
                    val sig = csrgDesc(moj2Obf, obf2Buk, nameDesc.substring(nameDesc.indexOf('(')), matcher.group(1))
                    val mojName = nameDesc.substring(0, nameDesc.indexOf('('))

                    if (obf == mojName || mojName.contains("$") || obf == "<init>" || obf == "<clinit>") {
                        continue
                    }
                    outFields.add("$currentClass $obf $sig $mojName")
                }
            }
        }

        outFields.sort()
        Files.write(fields.toPath(), outFields)
    }

    fun makeCombinedMaps(out: File, vararg members: File) {
        val combined = ArrayList(header)

        for (map in obf2Buk.entries) {
            combined.add(map.key + " " + map.value)
        }

        for (member in members) {
            for (read in member.toPath().readLines()) {
                var line = read
                if (line.startsWith("#")) {
                    continue
                }
                line = line.trim()

                val split = line.split(" ")
                if (split.size == 3) {
                    val clazz = split[0]
                    val orig = split[1]
                    val targ = split[2]

                    combined.add(deobfClass(clazz, obf2Buk.inverse()) + " " + orig + " " + targ)
                } else if (split.size == 4) {
                    val clazz = split[0]
                    val orig = split[1]
                    val desc = split[2]
                    val targ = split[3]

                    combined.add(
                        deobfClass(clazz, obf2Buk.inverse()) + " " + orig + " " + toObf(
                            desc,
                            obf2Buk.inverse()
                        ) + " " + targ
                    )
                }
            }
        }

        Files.write(out.toPath(), combined)
    }

    companion object {
        private val MEMBER_PATTERN: Pattern = Pattern.compile("(?:\\d+:\\d+:)?(.*?) (.*?) \\-> (.*)")
        fun deobfClass(obf: String, classMaps: Map<String, String>): String? {
            var obf = obf
            var buk = classMaps[obf]
            if (buk == null) {
                val inner = StringBuilder()

                while (buk == null) {
                    val idx = obf.lastIndexOf('$')
                    if (idx == -1) {
                        return null
                    }
                    inner.insert(0, obf.substring(idx))
                    obf = obf.substring(0, idx)

                    buk = classMaps[obf]
                }

                buk += inner
            }
            return buk
        }

        fun obfType(desc: String, map: Map<String, String>, out: StringBuilder): String {
            var size = 1
            when (desc[0]) {
                'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 'V' -> out.append(desc[0])
                '[' -> {
                    out.append("[")
                    return obfType(desc.substring(1), map, out)
                }

                'L' -> {
                    val type = desc.substring(1, desc.indexOf(";"))
                    size += type.length + 1
                    out.append("L").append(if (map.containsKey(type)) map[type] else type).append(";")
                }
            }
            return desc.substring(size)
        }

        fun toObf(desc: String, map: Map<String, String>): String {
            var modifiedDesc = desc.substring(1)
            val out = StringBuilder("(")
            if (modifiedDesc[0] == ')') {
                modifiedDesc = modifiedDesc.substring(1)
                out.append(')')
            }
            while (modifiedDesc.isNotEmpty()) {
                modifiedDesc = obfType(modifiedDesc, map, out)
                if (modifiedDesc.isNotEmpty() && modifiedDesc[0] == ')') {
                    modifiedDesc = modifiedDesc.substring(1)
                    out.append(')')
                }
            }
            return out.toString()
        }

        private fun csrgDesc(
            first: Map<String, String>,
            second: Map<String, String>,
            args: String,
            ret: String
        ): String {
            val parts =
                args.substring(1, args.length - 1).split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val desc = StringBuilder("(")
            for (part in parts) {
                if (part.isEmpty()) {
                    continue
                }
                desc.append(toJVMType(first, second, part))
            }
            desc.append(")")
            desc.append(toJVMType(first, second, ret))
            return desc.toString()
        }

        private fun toJVMType(first: Map<String, String>, second: Map<String, String>, type: String): String {
            when (type) {
                "byte" -> return "B"
                "char" -> return "C"
                "double" -> return "D"
                "float" -> return "F"
                "int" -> return "I"
                "long" -> return "J"
                "short" -> return "S"
                "boolean" -> return "Z"
                "void" -> return "V"
                else -> {
                    if (type.endsWith("[]")) {
                        return "[" + toJVMType(first, second, type.substring(0, type.length - 2))
                    }
                    val clazzType = type.replace('.', '/')
                    val obf = deobfClass(clazzType, first)
                    val mappedType = deobfClass(obf ?: clazzType, second)

                    return "L" + (mappedType ?: clazzType) + ";"
                }
            }
        }
    }
}
