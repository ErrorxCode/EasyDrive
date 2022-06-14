package apis.xcoder.easydrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * EasyDrive is a wrapper for the Google Drive API. It provides convenience methods for accessing the API.
 *
 * @author Rahil khan
 * @version 1.0
 */
public class EasyDrive {
    public final Drive drive;
    private String fileId;

    /**
     * Initialize the API client and construct the Drive service. This do not blocks the thread.
     *
     * @param clientId     Your clientID for the API
     * @param clientSecret Your clientSecret for the API
     * @param refreshToken Your refreshToken of the API authentication
     * @throws GeneralSecurityException if any of the credential is invalid or the server cannot authenticate.
     * @throws IOException              if the server cannot be reached or an networking exception occurred.
     */
    public EasyDrive(@Nonnull String clientId, @Nonnull String clientSecret, @Nonnull String refreshToken) throws GeneralSecurityException, IOException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory factory = GsonFactory.getDefaultInstance();
        Credential credential = new GoogleCredential.Builder()
                .setClientSecrets(clientId, clientSecret)
                .setJsonFactory(factory)
                .setTransport(transport)
                .build()
                .setRefreshToken(refreshToken);
        
        HttpRequestInitializer initializer = new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                credential.initialize(request);
                request.setWriteTimeout(10*60*1000);
                request.setReadTimeout(10*60000);
            }
        };
        drive = new Drive.Builder(transport, factory, initializer).setApplicationName("EasyDrive").build();
    }

    /**
     * Uploads a file to the Google Drive or updates it if already exist with progress.
     *
     * @param file     The file to upload
     * @param folderId The folder to upload the file to, null for root.
     */
    public AsyncTask<String> uploadFile(@Nonnull java.io.File file, @Nullable String folderId) {
        return AsyncTask.callAsync(() -> {
            fileId = AsyncTask.await(getFileId(file.getName(), folderId), 5);
            String mime = file.toURL().openConnection().getContentType();
            File body = new File().setName(file.getName()).setMimeType(mime);
            AbstractGoogleClientRequest<File> request;
            if (fileId == null) {
                body.setParents(Collections.singletonList(folderId == null ? "root" : folderId));
                request = drive.files().create(body, new FileContent(mime, file));
            } else {
                request = drive.files().update(fileId, body, new FileContent(mime, file));
            }
            return request.execute().getId();
        });
    }

    /**
     * Uploads a file as input-stream to the Google Drive or updates it if already exist.
     *
     * @param in       The stream from where to read the file.
     * @param folderId The folder to upload the file to, null for root.
     * @param listener The interface to monitor the progress of the upload.
     */
    public void uploadFile(@Nonnull String name, @Nonnull InputStream in, @Nullable String folderId,@Nonnull ProgressListener listener) {
        new Thread(() -> {
            try {
                fileId = AsyncTask.await(getFileId(name, folderId), 5);
                String mime = "application/octet-stream";
                File body = new File().setName(name).setMimeType(mime);
                AbstractGoogleClientRequest<File> request;
                AbstractInputStreamContent content = new AbstractInputStreamContent(mime) {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        return in;
                    }

                    @Override
                    public long getLength() throws IOException {
                        return in.available();
                    }

                    @Override
                    public boolean retrySupported() {
                        return false;
                    }
                };
                if (fileId == null) {
                    body.setParents(Collections.singletonList(folderId == null ? "root" : folderId));
                    request = drive.files().create(body,content);
                } else {
                    request = drive.files().update(fileId, body,content);
                }
                MediaHttpUploader uploader = request.getMediaHttpUploader();
                uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                uploader.setProgressListener(client -> listener.onProgress((int) (client.getProgress() * 100)));
                listener.onFinish(request.execute().getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Creates a folder if not already exist.
     *
     * @param name     The name of the folder
     * @param folderId The folder to create the folder in, null for root.
     * @return Call with the created folder ID
     */
    public AsyncTask<String> createFolder(@Nonnull String name, @Nullable String folderId) {
        return AsyncTask.callAsync(() -> {
            String id = AsyncTask.await(getFileId(name, folderId), 5);
            File body = new File()
                    .setName(name)
                    .setParents(Collections.singletonList(folderId == null ? "root" : folderId))
                    .setMimeType("application/vnd.google-apps.folder");
            if (id == null)
                return drive.files().create(body).execute().getId();
            else
                return id;
        });
    }

    /**
     * Creates an empty folder recursively from the given path.
     * For example, if the path is "folder1/folder2/mainFolder", it will create the folders "folder1" and "folder2"
     * and then create the file "mainFolder" in the "folder2" folder, ONLY IF NOT ALREADY EXIST.
     *
     * @param path The full path of the folder from the root along to the file name.
     * @return Call with the folder ID if created, or exception otherwise.
     */
    public AsyncTask<String> createFolderRecursively(@Nonnull String path){
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        var parts = path.split("/");
        return AsyncTask.callAsync(() -> {
            String parent = "root";
            for (String folder : parts) {
                parent = AsyncTask.await(createFolder(folder, parent), 5);
            }
            return parent;
        });
    }

    /**
     * Updates the file in the drive with the new file
     *
     * @param fileId      The file to update
     * @param updatedFile The new file
     * @return Call with the updated file ID
     */
    public AsyncTask<String> updateFile(@Nonnull String fileId, @Nonnull java.io.File updatedFile) {
        return AsyncTask.callAsync(() -> drive.files().update(fileId, new File(), new FileContent(updatedFile.toURL().openConnection().getContentType(), updatedFile)).execute().getId());
    }

    /**
     * Updates the text file in the drive with the new content. Do not use this for updating binary files.
     *
     * @param fileId  The file to update
     * @param content The new content
     * @return Call indication success or failure.
     */
    public AsyncTask<Void> updateFile(@Nonnull String fileId, @Nonnull String content) {
        return AsyncTask.callAsync(() -> {
            drive.files().update(fileId, new File(), ByteArrayContent.fromString("text/plain", content)).execute();
            return null;
        });
    }

    /**
     * Creates a text file in the specified folder (root if null), Updates if already exist.
     *
     * @param name     The name of the file
     * @param content  The content of the file
     * @param folderId The folder to create the file in, null for root.
     * @return Call with the created file ID
     */
    public AsyncTask<String> createTxtFile(@Nonnull String name, @Nonnull String content, @Nullable String folderId) {
        return AsyncTask.callAsync(() -> {
            String id = AsyncTask.await(getFileId(name, folderId), 5);
            File body = new File()
                    .setName(name)
                    .setMimeType("text/plain");
            if (id == null){
                body.setParents(Collections.singletonList(folderId == null ? "root" : folderId));
                return drive.files().create(body, new ByteArrayContent("text/plain", content.getBytes())).execute().getId();
            } else
                return drive.files().update(id, body, new ByteArrayContent("text/plain", content.getBytes())).execute().getId();
        });
    }

    /**
     * Gets the input stream of the file resource located on your drive. You can download the file using it but
     * don't forget to close it. Note that the input-stream returned is not buffered and {@code available()} method
     * may return 0.
     *
     * @param id The file id
     * @return Call with the input stream of the file
     */
    public AsyncTask<InputStream> getAsInputStream(String id) {
        return AsyncTask.callAsync(() -> drive.files().get(id).executeMedia().getContent());
    }

    /**
     * Downloads the file from the drive to the directory provided. This method runs synchronously.
     *
     * @param fileId    The file id of the file to download
     * @param directory The directory to download the file to
     * @param listener  The listener for monitoring the download progress
     */
    public void download(@Nonnull String fileId, @Nonnull String directory, @Nonnull ProgressListener listener) {
        try {
            InputStream in = AsyncTask.await(getAsInputStream(fileId), 5);
            File file = drive.files().get(fileId).setFields("name,size").execute();
            String name = file.getName();
            OutputStream os = new FileOutputStream(new java.io.File(directory, name));
            int read;
            long currentBytes = 0;
            byte[] bytes = new byte[1024];
            while ((read = in.read(bytes)) != -1) {
                currentBytes += read;
                os.write(bytes, 0, read);
                listener.onProgress((int) ((currentBytes * 100) / file.getSize()));
            }
            os.close();
            in.close();
            listener.onFinish(null);
        } catch (Exception e) {
            listener.onFailed(e);
        }
    }

    /**
     * Directly reads the contents of the file without downloading it. This method runs asynchronously
     * and is not intended to be used to read large files.
     *
     * @param fileId The file id
     * @return Call with the contents of the file
     */
    public AsyncTask<byte[]> getContent(@Nonnull String fileId) {
        return AsyncTask.callAsync(() -> {
            InputStream in = AsyncTask.await(getAsInputStream(fileId), 5);
            byte[] bytes = new byte[drive.files().get(fileId).setFields("size").execute().getSize().intValue()];
            in.read(bytes);
            return bytes;
        });
    }

    /**
     * Gets the size of the file with the given id.
     *
     * @param id The file id
     * @return Call with the file size
     */
    public AsyncTask<Long> getFileSize(String id) {
        return AsyncTask.callAsync(() -> {
            try {
                FileList result = drive.files().list()
                        .setQ("'" + id + "' in parents")
                        .setSpaces("drive")
                        .execute();
                long size = 0;
                for (File file : result.getFiles()) {
                    if (file.getMimeType().equals("application/vnd.google-apps.folder"))
                        size += AsyncTask.await(getFileSize(id), 5);
                    else
                        size += file.getSize();
                }
                return size;
            } catch (IOException e) {
                e.printStackTrace();
                return 0L;
            }
        });
    }

    /**
     * Gets the name of the file with the given name and folder id.
     *
     * @param id The file id
     * @return Call with the file name
     */
    public AsyncTask<String> getName(String id) {
        return AsyncTask.callAsync(() -> drive.files().get(id).setFields("name").execute().getName());
    }

    /**
     * Deletes a file or a folder
     *
     * @param fileId The file ID to delete
     * @return A void call
     */
    public AsyncTask<Void> delete(@Nonnull String fileId) {
        return AsyncTask.callAsync(() -> {
            drive.files().delete(fileId).execute();
            return null;
        });
    }

    /**
     * Search for a file in the Google Drive by its name and then returns its id if found.
     *
     * @param fileName The name of the file to search for
     * @param folderId The folder to search in, null for root.
     * @return Call with the file ID if found, null otherwise.
     */
    public AsyncTask<String> getFileId(@Nonnull String fileName, @Nullable String folderId) {
        return AsyncTask.callAsync(() -> {
            List<File> files = drive.files()
                    .list()
                    .setQ("name = '" + fileName + "' and '" + (folderId == null ? "root" : folderId) + "' in parents")
                    .execute()
                    .getFiles();
            if (files.size() == 0)
                return null;
            else
                return files.get(0).getId();
        });
    }

    /**
     * Lists the files of a folder
     *
     * @param folderId The folder for which you want to list files
     * @return AsyncTask of {@link FileMetadata[]} as holder of file meta-info
     */
    public AsyncTask<FileMetadata[]> listFiles(@Nonnull String folderId) {
        return AsyncTask.callAsync(() -> {
            FileList list = drive.files().list().setQ("'" + folderId + "' in parents").setFields("files(name,size,id,mimeType)").execute();
            FileMetadata[] array = new FileMetadata[list.getFiles().size()];
            for (int i = 0; i < array.length; i++) {
                File file = list.getFiles().get(i);
                array[i] = new FileMetadata(file.getName(), file.getId(), file.getMimeType(), (int) (file.getSize() / (1024 * 1024)));
            }
            System.out.println("Listing files in folder " + array.length);
            return array;
        });
    }

    /**
     * Creates an empty file recursively from the given path.
     * For example, if the path is "folder1/folder2/file.txt", it will create the folders "folder1" and "folder2"
     * and then create the file "file.txt" in the "folder2" folder, ONLY IF NOT ALREADY EXIST.
     *
     * @param path The full path of the file from the root along to the file name.
     * @return Call with the file ID if created, or exception otherwise.
     */
    public AsyncTask<String> createFileRecursively(@Nonnull String path) {
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        var parts = path.split("/");
        return AsyncTask.callAsync(() -> {
            String parent = "root";
            for (int i = 0; i < parts.length; i++) {
                if (i == parts.length - 1)
                    parent = AsyncTask.await(createTxtFile(parts[i], "", parent), 5);
                else
                    parent = AsyncTask.await(createFolder(parts[i], parent), 5);
            }
            return parent;
        });
    }

    /**
     * Interface for tracking the download/upload progress
     */
    public interface ProgressListener {
        void onProgress(int percentage);

        void onFinish(@Nullable String fileId);

        void onFailed(@Nonnull Exception e);
    }
}
