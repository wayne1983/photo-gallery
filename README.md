# 分布式图片存储系统
提供可伸缩的、容灾性的分布式图片存储系统。支持异步读写文件，支持负载均衡，以及快速部署等特性。

### 快速开始
在存储节点和监视节点已经部署的情况下，通过以下三部便可使用：

#### 第一步：引入库
Gradle:

在build.gradle文件dependencies块增加以下语句：

```
implementation: grpoup: 'net.threeple.pg', name: 'photo-gallery-api', version: '0.6.7'
```

#### 第二步：设置监视器地址：
可在三个地方设置，依优先级顺序分别为：操作系统环境变量、类路径下的pg.conf文件、用户家目录下的pg.conf文件。

```
monitors=10.1.12.134:6668
```

#### 第三步：定义成员变量
Java:

SomeoneService.java

```java
public class SomeoneService {
	private AsyncDownloader downloader = PhotoStorageFactory.getPhotoStorage(false);
	private AsyncUploader uploader = PhotoStorageFactory.getPhotoStorage(false);
	
	
	public void upload() throws IOException, InterruptedException, ExecutionException {
		String uri = "/path/to/someone.jpg";
		byte[] body = FileUtils.read(uri);
		Future<Response> future = uploader.asyncUpload(uri, body);
		Response r = future.get();
		if(200 >= r.getStatusCode() && 300 < r.getStatusCode()) {
			// 上传成功，接下来的业务逻辑
		} else {
			// 失败，异常处理逻辑
		}
	}
	
	public byte[] download() throws IOException, InterruptedException, ExecutionException {
		String uri = "/path/to/someone.png";
		Future<Response> future = downloader.asyncDownload(uri);
		Response r = future.get();
		if(200 >= r.getStatusCode() && 300 < r.getStatusCode()) {
			return r.getBody();
		} else {
			throw new IOException("下载失败");
		}
	}
}
```