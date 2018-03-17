/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ballerinalang.nativeimpl.compression;

import org.ballerinalang.bre.Context;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.natives.AbstractNativeFunction;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Native function ballerina.compression:unzipBytes.
 *
 * @since 0.964
 */
@BallerinaFunction(
        orgName = "ballerina",
        packageName = "compression",
        functionName = "unzipBytes",
        args = {@Argument(name = "content", type = TypeKind.BLOB),
                @Argument(name = "destDir", type = TypeKind.STRING),
                @Argument(name = "folderToUnzip", type = TypeKind.STRING)},
        isPublic = true
)
public class UnzipBytes extends AbstractNativeFunction {
    private static final Logger log = LoggerFactory.getLogger(UnzipBytes.class);
    /**
     * File content as byte array defined.
     */
    private static final int SRC_AS_BYTEARRAY_FIELD_INDEX = 0;
    /**
     * File path of the destination directory.
     */
    private static final int DEST_PATH_FIELD_INDEX = 0;

    /**
     * Folder to unzip from the compressed bytes.
     */
    private static final int FOLDER_TO_UNZIP_INDEX = 1;

    /**
     * Decompress/unzip byte arrays/blob.
     *  @param fileContentAsByteArray file content as a byte arry
     * @param outputFolder           destination folder
     * @param folderToUnzip folder to unzip
     */
    protected static void decompress(byte[] fileContentAsByteArray, String outputFolder, String folderToUnzip) {
        ZipInputStream zin = null;
        try {
            Path outdir = Paths.get(outputFolder);
            zin = new ZipInputStream(new ByteArrayInputStream(fileContentAsByteArray));
            ZipEntry entry;
            String name, dir;
            while ((entry = zin.getNextEntry()) != null) {
                name = entry.getName();
                if (!folderToUnzip.isEmpty() && name.startsWith(folderToUnzip)) {
                    int index = name.lastIndexOf('/') + 1;
                    name = name.substring(index);
                }
                    if (entry.isDirectory()) {
                        mkdirs(outdir, name);
                        continue;
                    }
                    // this part is necessary because file entry can come before directory entry where the
                    // file is located
                    dir = getDirectoryPath(name);
                    if (dir != null) {
                        mkdirs(outdir, dir);
                    }
                    extractFile(zin, outdir, name);
            }
        } catch (IOException e) {
            log.error("I/O Exception when processing files " + e.getMessage());
        } finally {
            try {
                if (zin != null) {
                    zin.close();
                }
            } catch (IOException e) {
                log.error("I/O Exception when closing the input stream " + e.getMessage());
            }
        }
    }

    /**
     * Extract files from the zipInputStream.
     *
     * @param in     zipInputStream object
     * @param outdir output directory file
     * @param name   name of the file
     */
    private static void extractFile(ZipInputStream in, Path outdir, String name) {
        byte[] buffer = new byte[4096];
        BufferedOutputStream out = null;
        try {
            Path resourcePath = Paths.get(outdir.toString()).resolve(name);
            out = new BufferedOutputStream(new FileOutputStream(resourcePath.toString()));
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        } catch (FileNotFoundException e) {
            log.error("File not found to process " + outdir + e.getMessage());
        } catch (IOException e) {
            log.error("I/O Exception when closing the input stream " + outdir +  e.getMessage());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.error("I/O Exception when closing the input stream " + e.getMessage());
                }
            }
        }
    }

    /**
     * Create the directory name.
     *
     * @param name name of the directory
     * @return directory name
     */
    private static String getDirectoryPath(String name) {
        if (name != null) {
            int s = name.lastIndexOf(File.separatorChar);
            return s == -1 ? null : name.substring(0, s);
        }
        return null;
    }

    /**
     * Make directories if they doesn't exists.
     *
     * @param outdir destination file
     * @param path   path of the destination directory
     */
    private static boolean mkdirs(Path outdir, String path) throws IOException {
        Path dir = outdir.resolve(path);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            return true;
        }
        return false;
    }

    @Override
    public BValue[] execute(Context context) {
        byte[] content = getBlobArgument(context, SRC_AS_BYTEARRAY_FIELD_INDEX);
        String destDir = getStringArgument(context, DEST_PATH_FIELD_INDEX);
        String folderToUnzip = getStringArgument(context, FOLDER_TO_UNZIP_INDEX);
        decompress(content, destDir, folderToUnzip);
        return VOID_RETURN;
    }
}
