package com.imaia.storage;

import com.imaia.storage.MediaStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 媒体二进制仓库：S3 兼容端点（rustfs/MinIO 等），唯一后端。storageRef 即 object key
 * （category/yyyy/mm/uuid.ext）。构造时 bucket 不存在即创建（幂等；生产可预建并收紧写权限）。
 */
@Component
public class S3MediaStorage {

    private final MinioClient client;
    private final String bucket;

    public S3MediaStorage(MediaStorageProperties properties) {
        if (isBlank(properties.endpoint()) || isBlank(properties.accessKey()) || isBlank(properties.secretKey())) {
            throw new IllegalStateException(
                    "imaia.storage 的 endpoint/access-key/secret-key 不能为空（凭据经环境变量注入）。");
        }
        // 显式超时：minio 默认 okhttp 配置等待过长，会拖死事件流线程。
        this.client = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .httpClient(new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .callTimeout(120, TimeUnit.SECONDS)
                        .build())
                .build();
        this.bucket = isBlank(properties.bucket()) ? "imaia-media" : properties.bucket();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception failure) {
            throw new IllegalStateException("S3 媒体仓库不可用（bucket=" + bucket + "）：" + failure.getMessage(), failure);
        }
    }

    /** 落盘一段媒体字节，返回可写回 storage_ref 的 object key。 */
    public String store(String category, String filename, byte[] data) {
        String key = StorageRefs.key(category, filename);
        try (var input = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(input, data.length, -1)
                    .build());
        } catch (Exception failure) {
            throw new IllegalStateException("S3 媒体写入失败：" + key, failure);
        }
        return key;
    }

    /** 读回一段媒体字节；空引用或对象不存在返回 empty。 */
    public Optional<byte[]> read(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        try (var input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(ref).build())) {
            return Optional.of(input.readAllBytes());
        } catch (ErrorResponseException notFound) {
            String code = notFound.errorResponse() == null ? "" : notFound.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                return Optional.empty();
            }
            throw new IllegalStateException("S3 媒体读取失败：" + ref, notFound);
        } catch (Exception failure) {
            throw new IllegalStateException("S3 媒体读取失败：" + ref, failure);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
