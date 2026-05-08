package com.minduc.happabi.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * Tạo wrapper và đọc toàn bộ body vào cache ngay lập tức.
     * Chỉ đọc từ original stream 1 lần duy nhất.
     */
    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * Luôn trả về stream mới từ byte array cache.
     * Có thể gọi nhiều lần — mỗi lần bắt đầu từ đầu.
     */
    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(new ByteArrayInputStream(cachedBody));
    }

    /**
     * Tương tự getInputStream() nhưng trả về Reader.
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody)));
    }

    /**
     * Trả về byte array cache để Filter đọc trực tiếp mà không cần tạo stream.
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream stream;

        CachedBodyServletInputStream(ByteArrayInputStream stream) {
            this.stream = stream;
        }

        @Override
        public boolean isFinished() {
            return stream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Không cần implement cho synchronous filter
        }

        @Override
        public int read() {
            return stream.read();
        }
    }
}
