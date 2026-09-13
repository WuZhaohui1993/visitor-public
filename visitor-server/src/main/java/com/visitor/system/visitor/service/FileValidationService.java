package com.visitor.system.visitor.service;

import com.visitor.system.common.BusinessException;
import com.visitor.system.visitor.dto.UploadScene;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class FileValidationService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png");
    private static final int MAX_UPLOAD_SIZE = 5 * 1024 * 1024;
    private static final int MIN_FACE_IMAGE_SIZE = 10 * 1024;
    private static final int MAX_FACE_IMAGE_SIZE = 200 * 1024;

    public byte[] validate(MultipartFile file) {
        return validate(file, UploadScene.GENERAL).bytes();
    }

    public ValidatedFile validate(MultipartFile file, UploadScene scene) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new BusinessException("图片不能超过5MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException("仅支持 JPG/PNG 图片");
        }
        try {
            byte[] bytes = file.getBytes();
            if (!isJpeg(bytes) && !isPng(bytes)) {
                throw new BusinessException("图片内容校验失败，仅支持 JPG/PNG");
            }
            if (scene == UploadScene.FACE) {
                return normalizeFaceImage(readFaceImage(bytes));
            }
            return new ValidatedFile(bytes, detectExtension(bytes), detectContentType(bytes));
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    public String detectExtension(byte[] bytes) {
        if (isJpeg(bytes)) {
            return ".jpg";
        }
        if (isPng(bytes)) {
            return ".png";
        }
        throw new BusinessException("无法识别图片格式");
    }

    public byte[] prepareFaceImage(byte[] originalBytes) {
        return normalizeFaceImage(readFaceImage(originalBytes)).bytes();
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BusinessException("图片内容校验失败，请上传真实 JPG/PNG 图片");
            }
            return image;
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    private BufferedImage readFaceImage(byte[] bytes) {
        return applyExifOrientation(readImage(bytes), readExifOrientation(bytes));
    }

    private ValidatedFile normalizeFaceImage(BufferedImage source) {
        try {
            BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgbImage.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();

            byte[] jpegBytes = writeJpeg(rgbImage, 0.82f);
            if (jpegBytes.length > MAX_FACE_IMAGE_SIZE) {
                int targetWidth = Math.min(rgbImage.getWidth(), 640);
                int targetHeight = Math.max(1, rgbImage.getHeight() * targetWidth / Math.max(1, rgbImage.getWidth()));
                Image scaled = rgbImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
                BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D resizedGraphics = resized.createGraphics();
                resizedGraphics.setColor(Color.WHITE);
                resizedGraphics.fillRect(0, 0, targetWidth, targetHeight);
                resizedGraphics.drawImage(scaled, 0, 0, null);
                resizedGraphics.dispose();
                jpegBytes = writeJpeg(resized, 0.72f);
            }
            if (jpegBytes.length < MIN_FACE_IMAGE_SIZE || jpegBytes.length > MAX_FACE_IMAGE_SIZE) {
                throw new BusinessException("人脸照片不符合海康要求，请上传光线充足、无遮挡的清晰正脸照");
            }
            return new ValidatedFile(jpegBytes, ".jpg", "image/jpeg");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("处理人脸照片失败，请重新上传清晰正脸照");
        }
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
        return outputStream.toByteArray();
    }

    private int readExifOrientation(byte[] bytes) {
        if (!isJpeg(bytes)) {
            return 1;
        }
        int offset = 2;
        while (offset + 4 <= bytes.length) {
            if ((bytes[offset] & 0xFF) != 0xFF) {
                break;
            }
            int marker = bytes[offset + 1] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            if (marker == 0x00 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                offset += 2;
                continue;
            }
            int segmentLength = readUnsignedShort(bytes, offset + 2, false);
            if (segmentLength < 2 || offset + 2 + segmentLength > bytes.length) {
                break;
            }
            if (marker == 0xE1
                && segmentLength >= 10
                && bytes[offset + 4] == 0x45
                && bytes[offset + 5] == 0x78
                && bytes[offset + 6] == 0x69
                && bytes[offset + 7] == 0x66
                && bytes[offset + 8] == 0x00
                && bytes[offset + 9] == 0x00) {
                return parseExifOrientation(bytes, offset + 10, segmentLength - 8);
            }
            offset += segmentLength + 2;
        }
        return 1;
    }

    private int parseExifOrientation(byte[] bytes, int tiffStart, int tiffLength) {
        if (tiffLength < 14 || tiffStart + tiffLength > bytes.length) {
            return 1;
        }
        boolean littleEndian;
        if (bytes[tiffStart] == 0x49 && bytes[tiffStart + 1] == 0x49) {
            littleEndian = true;
        } else if (bytes[tiffStart] == 0x4D && bytes[tiffStart + 1] == 0x4D) {
            littleEndian = false;
        } else {
            return 1;
        }
        if (readUnsignedShort(bytes, tiffStart + 2, littleEndian) != 42) {
            return 1;
        }
        long ifdOffset = readUnsignedInt(bytes, tiffStart + 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset + 2 > tiffLength) {
            return 1;
        }
        int directoryStart = tiffStart + (int) ifdOffset;
        int entryCount = readUnsignedShort(bytes, directoryStart, littleEndian);
        for (int index = 0; index < entryCount; index++) {
            int entryOffset = directoryStart + 2 + index * 12;
            if (entryOffset + 12 > tiffStart + tiffLength) {
                break;
            }
            if (readUnsignedShort(bytes, entryOffset, littleEndian) != 0x0112) {
                continue;
            }
            int type = readUnsignedShort(bytes, entryOffset + 2, littleEndian);
            long count = readUnsignedInt(bytes, entryOffset + 4, littleEndian);
            if (type != 3 || count < 1) {
                return 1;
            }
            int orientation = readUnsignedShort(bytes, entryOffset + 8, littleEndian);
            return orientation >= 1 && orientation <= 8 ? orientation : 1;
        }
        return 1;
    }

    private int readUnsignedShort(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
        }
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private long readUnsignedInt(byte[] bytes, int offset, boolean littleEndian) {
        if (littleEndian) {
            return ((long) bytes[offset] & 0xFF)
                | (((long) bytes[offset + 1] & 0xFF) << 8)
                | (((long) bytes[offset + 2] & 0xFF) << 16)
                | (((long) bytes[offset + 3] & 0xFF) << 24);
        }
        return (((long) bytes[offset] & 0xFF) << 24)
            | (((long) bytes[offset + 1] & 0xFF) << 16)
            | (((long) bytes[offset + 2] & 0xFF) << 8)
            | ((long) bytes[offset + 3] & 0xFF);
    }

    private BufferedImage applyExifOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = orientation >= 5 ? height : width;
        int targetHeight = orientation >= 5 ? width : height;
        int imageType = source.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage rotated = new BufferedImage(targetWidth, targetHeight, imageType);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> {
                        targetX = width - 1 - x;
                        targetY = y;
                    }
                    case 3 -> {
                        targetX = width - 1 - x;
                        targetY = height - 1 - y;
                    }
                    case 4 -> {
                        targetX = x;
                        targetY = height - 1 - y;
                    }
                    case 5 -> {
                        targetX = y;
                        targetY = x;
                    }
                    case 6 -> {
                        targetX = height - 1 - y;
                        targetY = x;
                    }
                    case 7 -> {
                        targetX = height - 1 - y;
                        targetY = width - 1 - x;
                    }
                    case 8 -> {
                        targetX = y;
                        targetY = width - 1 - x;
                    }
                    default -> {
                        targetX = x;
                        targetY = y;
                    }
                }
                rotated.setRGB(targetX, targetY, rgb);
            }
        }
        return rotated;
    }

    private String detectContentType(byte[] bytes) {
        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPng(bytes)) {
            return "image/png";
        }
        throw new BusinessException("无法识别图片格式");
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length > 3
            && (bytes[0] & 0xFF) == 0xFF
            && (bytes[1] & 0xFF) == 0xD8
            && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length > 8
            && (bytes[0] & 0xFF) == 0x89
            && bytes[1] == 0x50
            && bytes[2] == 0x4E
            && bytes[3] == 0x47;
    }

    public record ValidatedFile(byte[] bytes, String extension, String contentType) {
    }
}
