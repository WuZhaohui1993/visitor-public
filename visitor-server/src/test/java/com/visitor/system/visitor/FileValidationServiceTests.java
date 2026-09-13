package com.visitor.system.visitor;

import com.visitor.system.visitor.dto.UploadScene;
import com.visitor.system.visitor.service.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileValidationServiceTests {

    private FileValidationService fileValidationService;

    @BeforeEach
    void setUp() {
        fileValidationService = new FileValidationService();
    }

    @Test
    void shouldRotateExifOrientedFaceUploadBeforeSaving() throws Exception {
        byte[] sourceBytes = createExifOrientedJpeg(900, 600, 6);

        MockMultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", sourceBytes);
        FileValidationService.ValidatedFile validatedFile = fileValidationService.validate(file, UploadScene.FACE);
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(validatedFile.bytes()));

        assertThat(normalized).isNotNull();
        assertThat(normalized.getHeight()).isGreaterThan(normalized.getWidth());
        assertThat(validatedFile.contentType()).isEqualTo("image/jpeg");
        assertThat(validatedFile.extension()).isEqualTo(".jpg");
    }

    @Test
    void shouldRotateExifOrientedStoredFaceImageBeforeHikvisionSync() throws Exception {
        byte[] sourceBytes = createExifOrientedJpeg(900, 600, 6);

        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(fileValidationService.prepareFaceImage(sourceBytes)));

        assertThat(normalized).isNotNull();
        assertThat(normalized.getHeight()).isGreaterThan(normalized.getWidth());
    }

    private byte[] createExifOrientedJpeg(int width, int height, int orientation) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = (x * 17 + y * 7) & 0xFF;
                int green = (x * 9 + y * 13) & 0xFF;
                int blue = (x * 5 + y * 19) & 0xFF;
                image.setRGB(x, y, new Color(red, green, blue).getRGB());
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.fillOval(width / 4, height / 6, width / 2, height / 2);
        graphics.setColor(Color.BLACK);
        graphics.fillOval(width / 2 - 90, height / 2 - 40, 30, 30);
        graphics.fillOval(width / 2 + 60, height / 2 - 40, 30, 30);
        graphics.drawArc(width / 2 - 100, height / 2 - 10, 200, 120, 210, 120);
        graphics.dispose();

        ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpegOutput);
        return withExifOrientation(jpegOutput.toByteArray(), orientation);
    }

    private byte[] withExifOrientation(byte[] jpegBytes, int orientation) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(jpegBytes.length + 40);
        output.write(0xFF);
        output.write(0xD8);
        output.writeBytes(buildExifSegment(orientation));
        output.write(jpegBytes, 2, jpegBytes.length - 2);
        return output.toByteArray();
    }

    private byte[] buildExifSegment(int orientation) {
        return new byte[] {
            (byte) 0xFF, (byte) 0xE1,
            0x00, 0x22,
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00,
            0x4D, 0x4D, 0x00, 0x2A,
            0x00, 0x00, 0x00, 0x08,
            0x00, 0x01,
            0x01, 0x12,
            0x00, 0x03,
            0x00, 0x00, 0x00, 0x01,
            0x00, (byte) orientation, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
        };
    }
}
