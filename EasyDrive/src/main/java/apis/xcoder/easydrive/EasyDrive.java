package apis.xcoder.easydrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
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
    public EasyDrive(@Nonnull String clientId, @Nonnull String clientSecret,@Nonnull String refreshToken) throws GeneralSecurityException, IOException {
        NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory factory = GsonFactory.getDefaultInstance();
        Credential credential = new GoogleCredential.Builder()
                .setClientSecrets(clientId, clientSecret)
                .setJsonFactory(factory)
                .setTransport(transport)
                .build()
                .setRefreshToken(refreshToken);
        drive = new Drive.Builder(transport, factory, credential).setApplicationName("EasyDrive").build();
    }

    /**
     * Uploads a file to the Google Drive or updates it if already exist with progress.
     *
     * @param file     The file to upload
     * @param folderId The folder to upload the file to, null for root.
     * @param listener The interface to monitor the progress of the upload.
     */
    public void uploadFile(@Nonnull java.io.File file, @Nullable String folderId, @Nonnull ProgressListener listener) {
        new Thread(() -> {
            try {
                fileId = AsyncTask.await(getFileId(file.getName(), folderId),5);
                String mime = Files.probeContentType(file.toPath());
                File body = new File().setName(file.getName()).setMimeType(mime);
                AbstractGoogleClientRequest<File> request;
                MediaHttpUploader uploader;
                if (fileId == null) {
                    body.setParents(Collections.singletonList(folderId == null ? "root" : folderId));
                    request = drive.files().create(body, new FileContent(mime, file));
                    uploader = request.getMediaHttpUploader();
                } else {
                    request = drive.files().update(fileId, body, new FileContent(mime, file));
                    uploader = request.getMediaHttpUploader();
                }
                uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);
                uploader.setProgressListener(client -> listener.onProgress((int) (client.getProgress() * 100)));
                listener.onFinish(request.execute().getId());
            } catch (Exception e) {
                listener.onFailed(e);
            }
        }).start();
    }

    /**
     * Uploads a file as byte array to the Google Drive or updates it if already exist.
     *
     * @param bytes    The bytes to upload
     * @param folderId The folder to upload the file to, null for root.
     * @return Call with the uploaded file ID
     */
    public AsyncTask<String> uploadFile(@Nonnull String name, @Nonnull byte[] bytes, @Nullable String folderId) {
        return AsyncTask.callAsync(() -> {
            String id = AsyncTask.await(getFileId(name, folderId),5);
            String mime = "binary/octet-stream";
            File body = new File()
                    .setName(name)
                    .setParents(Collections.singletonList(folderId == null ? "root" : folderId))
                    .setMimeType(mime);
            if (id == null)
                return drive.files().create(body, new ByteArrayContent(mime, bytes)).execute().getId();
            else
                return drive.files().update(id, body, new ByteArrayContent(mime, bytes)).execute().getId();
        });
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
            String id = AsyncTask.await(getFileId(name, folderId),5);
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
     * Creates a text file in the specified folder (root if null), Updates if already exist.
     *
     * @param name     The name of the file
     * @param content  The content of the file
     * @param folderId The folder to create the file in, null for root.
     * @return Call with the created file ID
     */
    public AsyncTask<String> createTxtFile(@Nonnull String name, @Nonnull String content, @Nullable String folderId) {
        return AsyncTask.callAsync(() -> {
            String id = AsyncTask.await(getFileId(name, folderId),5);
            File body = new File()
                    .setName(name)
                    .setParents(Collections.singletonList(folderId == null ? "root" : folderId))
                    .setMimeType("text/plain");
            if (id == null)
                return drive.files().create(body, new ByteArrayContent("text/plain", content.getBytes())).execute().getId();
            else
                return drive.files().update(id, body, new ByteArrayContent("text/plain", content.getBytes())).execute().getId();
        });
    }

    /**
     * Gets the input stream of the file resource located on your drive. You can download the file using it but
     * don't forget to close it. Note that the input-stream returned is not buffered and {@code available()} method
     * may return 0.
     * @param id The file id
     * @return Call with the input stream of the file
     */
    public AsyncTask<InputStream> getAsInputStream(String id) {
        return AsyncTask.callAsync(() -> drive.files().get(id).executeMedia().getContent());
    }

    /**
     * Downloads the file from the drive to the directory provided. This method runs asynchronously.
     * @param fileId The file id of the file to download
     * @param directory The directory to download the file to
     * @param listener The listener for monitoring the download progress
     */
    public void download(@Nonnull String fileId,@Nonnull String directory, @Nonnull ProgressListener listener) {
        getAsInputStream(fileId).setOnCompleteCallback(task -> {
            if (task.isSuccessful){
                try {
                    File file = drive.files().get(fileId).setFields("name,size").execute();
                    String name = file.getName();
                    InputStream in = task.result;
                    OutputStream os = new FileOutputStream(new java.io.File(directory,name));
                    int read;
                    long currentBytes = 0;
                    byte[] bytes = new byte[1024];
                    while ((read = in.read(bytes)) != -1) {
                        currentBytes += read;
                        os.write(bytes, 0, read);
                        listener.onProgress((int) ((currentBytes*100)/file.getSize()));
                    }
                    os.close();
                    in.close();
                } catch (IOException e) {
                    listener.onFailed(e);
                }
            } else
                listener.onFailed(task.exception);
        });
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
     * Interface for tracking the download/upload progress
     */
    public interface ProgressListener {
        void onProgress(int percentage);

        void onFinish(@Nullable String fileId);

        void onFailed(@Nonnull Exception e);
    }
}