
# EasyDrive ~ Google drive API made easy :-)
<p align="left">
  <a href="#"><img alt="Version" src="https://img.shields.io/badge/Version-1.1-green"></a>
  <a href="https://www.instagram.com/x__coder__x/"><img alt="Instagram - x__coder__" src="https://img.shields.io/badge/Instagram-x____coder____x-lightgrey"></a>
  <a href="#"><img alt="GitHub Repo stars" src="https://img.shields.io/github/stars/ErrorxCode/OTP-Verification-Api?style=social"></a>
  </p>


The official java client for google drive API is hard of very poor design.
There is a need for a wrapper and here it is. This library provides you the high-level
abstraction to access the API. just a simple method call with only the required argument.

![easydrive](https://developers.google.com/workspace/images/banners/drive_856.png)


## Features

- Easy and simple
- Auto manage of credentials
- Supports **Downloading/Uploading file** (with progress) 
- Supports **Creating/Deleting file**
- Supports **Getting file meta-info**
- More features coming soon... 


## Deployment
#### For gradle

Add it in your root build.gradle at the end of repositories:

```groovy
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
Add the dependency, in module build. Gradle

```groovy
	dependencies {
	    implementation 'com.github.ErrorxCode:EasyDrive:Tag'
	}
```

#### For maven
Declaring jitpack repository
```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```
Adding the dependencies
```xml
	<dependency>
	    <groupId>com.github.ErrorxCode</groupId>
	    <artifactId>EasyDrive</artifactId>
	    <version>Tag</version>
	</dependency>
```


## Acknowledgements

 - [Google drive API](https://developers.google.com/drive/api)
 - [API Terms of serive](https://developers.google.com/drive/api/terms)
 - [Authenication guide](https://developers.google.com/workspace/guides/auth-overview)


## Usage/Examples
Altho using this library is very very easy, still, you can check some examples

### Initializing
Before using the library, you need to authenticate it to the user's drive.
Provide your credentials passing them to the constructor to get a reference of the class.
```java
try {
    EasyDrive drive = new EasyDrive(CLIENT_ID,CLIENT_SECRET,REFRESH_CODE);
} catch (GeneralSecurityException | IOException e) {
    e.printStackTrace();
}
```

### Uploading files
You can upload a file to drive using 2 ways. Either directly with `File` or with `bytes[]`

**Example**:
```java
drive.uploadFile(file,null, new ProgressListener() {
    @Override
    public void onProgress(int percentage) {
        
    }

    @Override
    public void onFinish(@Nullable String fileId) {
        // The id of the file uploaded
    }

    @Override
    public void onFailed(@Nonnull Exception e) {

    }
});
```
Here, the *2nd argument* is the folder id in which you want to upload the file.
`null` means in the root.


### Downloading files
Downloading files is also as easy as uploading them. See the example below

**Example:**
```java
drive.download(fileId,"D://Downloads", new ProgressListener() {
    @Override
    public void onProgress(int percentage) {
        
    }

    @Override
    public void onFinish(@Nullable String fileId) {
        // null in this case
    }

    @Override
    public void onFailed(@Nonnull Exception e) {

    }
});
```
If you don't know the file id but know the name & parent folder, you can get
the id using `getFileId(name,folder)` method.

### AsyncTasks
Now every method except the above 2, returns an `AsyncTask` object.
This is similar to the **Future** you get from **ExecutorService** or in a better way,
it is like a `Task` you get from firebase calls. To [learn more](https://gist.github.com/ErrorxCode/54c246dccbca058ebaf8ea15de2d1416?permalink_comment_id=4133135#gistcomment-4133135) about **AsyncTask**, click the link

#### Here is a quick tutorial,
given a task, you can set the callbacks to get the result

**Example:**
```java
task.setOnSuccessCallback(result -> {
    // your callable return value is here
}).setOnErrorCallback(e -> {
    // your callable exception is here
});
```

### Creating files/folders
You can directly create text files in the drive using the `creative()` method.
Or folder with `createFolder()`.

**Example**:
```java
AsyncTask<String> fileTask = drive.createTxtFile("file.txt", "Hello World", null);
AsyncTask<String> folderTask = drive.createFolder("folder", null);
```
The `null` is the folder id where you want to create the file, "root" in this case.


### Getting meta-info
You can get meta info of the file using the `getX()` method where **X**
is the field (like size, name, owner, etc..) you want to fetch.
**Example**:
```java
AsyncTask<Long> size = drive.getFileSize(fileId);
AsyncTask<String> name = drive.getName(fileId);
AsyncTask<String> id = drive.getFileId("test.txt", null);
drive.getAsInputStream(fileId);
```



## Contributing

Contributions are always welcome!

There is a scope for improvement in this library. What you can always do is 
you can add more API methods to the library.


## That's it
If you liked my hard work, you can show your support. I don't take donations,
you can star this repo instead. 

