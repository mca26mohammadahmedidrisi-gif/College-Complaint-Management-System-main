package com.college.complaint.service;

import com.college.complaint.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Component
public class ImageContentValidator {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;

    public ValidatedImage validate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file must not be empty");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image file must not exceed 5 MB");
        }

        String detectedType = hasPngSignature(bytes) ? "image/png"
                : hasJpegSignature(bytes) ? "image/jpeg" : null;
        if (detectedType == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only valid JPEG and PNG images are allowed");
        }
        if (declaredContentType != null && !declaredContentType.isBlank()
                && !MediaTypes.isGeneric(declaredContentType)
                && !detectedType.equalsIgnoreCase(declaredContentType.trim())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Image content does not match the declared media type");
        }

        verifyDecodableImage(bytes, detectedType);
        byte[] sanitizedBytes = stripMetadata(bytes, detectedType);
        if (sanitizedBytes.length == 0 || sanitizedBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The processed image must not exceed 5 MB");
        }
        return new ValidatedImage(detectedType, detectedType.equals("image/png") ? "png" : "jpg",
                sanitizedBytes.length, sanitizedBytes);
    }

    /** Re-encode pixels without copying EXIF, GPS, camera, or other embedded metadata. */
    private byte[] stripMetadata(byte[] bytes, String detectedType) {
        String format = detectedType.equals("image/png") ? "png" : "jpeg";
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image file content is invalid");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image format could not be processed");
            }
            return output.toByteArray();
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image file content is invalid");
        }
    }

    private void verifyDecodableImage(byte[] bytes, String detectedType) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image file content is invalid");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image file content is invalid");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String actualFormat = reader.getFormatName();
                boolean formatMatches = detectedType.equals("image/png")
                        ? "png".equalsIgnoreCase(actualFormat)
                        : "jpeg".equalsIgnoreCase(actualFormat) || "jpg".equalsIgnoreCase(actualFormat);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!formatMatches || width <= 0 || height <= 0
                        || (long) width * height > MAX_IMAGE_PIXELS || reader.read(0) == null) {
                    throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "Image file is invalid or exceeds the supported dimensions");
                }
            } finally {
                reader.dispose();
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image file content is invalid");
        }
    }

    private boolean hasPngSignature(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) return false;
        }
        return true;
    }

    private boolean hasJpegSignature(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF;
    }

    private static final class MediaTypes {
        private static boolean isGeneric(String contentType) {
            return "application/octet-stream".equalsIgnoreCase(contentType.trim());
        }
    }

    public record ValidatedImage(String contentType, String extension, long sizeBytes, byte[] sanitizedBytes) { }
}
