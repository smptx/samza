/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.samza.util

import java.io._
import java.nio.file._
import java.util.zip.CRC32

class FileUtil extends Logging {
  /**
    * Writes checksum & data to a file
    * Checksum is pre-fixed to the data and is a 32-bit long type data.
    * @param file The file handle to write to
    * @param data The data to be written to the file
    * */
  def writeWithChecksum(file: File, data: String): Unit = {
    val checksum = getChecksum(data)
    val tmpFilePath = file.getAbsolutePath + ".tmp"
    val tmpFile = new File(tmpFilePath)
    var oos: ObjectOutputStream = null
    var fos: FileOutputStream = null
    try {
      fos = new FileOutputStream(tmpFile)
      oos = new ObjectOutputStream(fos)
      oos.writeLong(checksum)
      oos.writeUTF(data)
    } finally {
      if (oos != null) oos.close()
      if (fos != null) fos.close()
    }

    //atomic swap of tmp and real offset file
    move(tmpFile, file)
  }

  /**
    * Writes the data to a text file
    * @param file The file handle to write to
    * @param data The data to be written to the file
    * @param append true for appending data to file, false otherwise
    * */
  def writeToTextFile(file: File, data: String, append: Boolean): Unit = {

    val tmpFilePath = file.getAbsolutePath + ".tmp"
    var fileWriter: FileWriter = null
    val tmpFile = new File(tmpFilePath)

    //atomic swap of tmp and real file if we need to append
    if (append) {
      move(file, tmpFile)
    }

    try {
      fileWriter = new FileWriter(tmpFile, append)
      fileWriter.write(data)
    } catch {
      case e: Exception =>
        error("Error in writing to file %s isAppend %s" format (file, append))
        System.out.println(e)
    } finally {
      if (fileWriter != null) fileWriter.close()
    }

    //atomic swap of tmp and real file
    move(tmpFile, file)
  }

  /**
   * Moves source file to destination file, replacing destination file if it already exists.
   */
  def move(source: File, destination: File) : Unit = {
    try {
      if (source.exists()) {
        Files.move(source.toPath, destination.toPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
    } catch {
      case e: AtomicMoveNotSupportedException =>
        Files.move(source.toPath, destination.toPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  /**
    * Reads from a file that has a checksum prepended to the data
    * @param file The file handle to read from
    * */
  def readWithChecksum(file: File): String = {
    var fis: FileInputStream = null
    var ois: ObjectInputStream = null
    try {
      fis = new FileInputStream(file)
      ois = new ObjectInputStream(fis)
      val checksumFromFile = ois.readLong()
      val data = ois.readUTF()
      if(checksumFromFile == getChecksum(data)) {
        data
      } else {
        info("Checksum match failed. Data in file is corrupted. Skipping content.")
        null
      }
    } finally {
      if (ois != null) ois.close()
      if (fis != null) fis.close()
    }
  }

  /**
    * Recursively remove a directory (or file), and all sub-directories. Equivalent
    * to rm -rf.
    */
  def rm(file: File): Unit = {
    if (file == null) {
      return
    } else if (file.isDirectory) {
      val files = file.listFiles()
      if (files != null) {
        for (f <- files)
          rm(f)
      }
      file.delete()
    } else {
      file.delete()
    }
  }

  def exists(path: Path): Boolean = {
    Files.exists(path)
  }

  def createDirectories(path: Path): Path = {
    // Files.createDirectories throws FileAlreadyExistsException if the path already exists
    // but the last dir in the path is a symlink to another dir. Check explicitly if the path
    // already exists to avoid this behavior.
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    } else {
      path
    }
  }

  /**
    * Generates the CRC32 checksum code for any given data
    * @param data The string for which checksum has to be generated
    * @return long type value representing the checksum
    * */
  def getChecksum(data: String): Long = {
    val crc = new CRC32
    crc.update(data.getBytes)
    crc.getValue
  }
}
