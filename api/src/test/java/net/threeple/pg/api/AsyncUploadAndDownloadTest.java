package net.threeple.pg.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.threeple.pg.api.cluster.SimpleMonitorServer;
import net.threeple.pg.api.factory.PhotoStorageFactory;
import net.threeple.pg.api.model.Response;
import net.threeple.pg.shared.util.FileUtils;

public class AsyncUploadAndDownloadTest {
	final Logger logger = LoggerFactory.getLogger(AsyncUploadAndDownloadTest.class);
	private AsyncUploader uploader;
	private AsyncDownloader downloader;
	private int threadQuantity = 3;
	
	@Before
	public void prepare() throws Exception {
		URL url = this.getClass().getClassLoader().getResource("test.properties");
		Properties prope = new Properties();
		prope.load(new FileInputStream(url.getPath()));
		
		String server = prope.getProperty("server");
		if("inner".equals(server)) {
			SimpleMonitorServer.start();
			SimplePsdServer.start();
		}
		this.threadQuantity = Integer.parseInt(prope.getProperty("thread_quantity", "3"));
		this.uploader = PhotoStorageFactory.getPhotoStorage(false);
		this.downloader = PhotoStorageFactory.getPhotoStorage(false);
		
		UploadUtils.emptyUploadDir();
	}
	
	@Test
	public void testUploadAndDownload() throws Exception {
		CountDownLatch begin = new CountDownLatch(1);
		CountDownLatch end = new CountDownLatch(threadQuantity * 2);
		
		BlockingQueue<PhotoFace> queue = new ArrayBlockingQueue<>(200);
		AtomicBoolean done = new AtomicBoolean(false);
		
		UploadWorker upWorker = new UploadWorker(begin, end, queue, done);
		DownloadWorker doWorker = new DownloadWorker(begin, end, queue, done);
		
		for(int i = 0; i < threadQuantity; i++) {
			Thread uploadThread = new Thread(upWorker, "Upload-Worker-Thread-" + i);
			uploadThread.start();
			Thread downloadThread = new Thread(doWorker, "Download-Worker-Thread-" + i);
			downloadThread.start();
		}
		
		begin.countDown();
		end.await();
		
		Thread.sleep(100 * threadQuantity * 2);
	}
	
	private class UploadWorker implements Runnable {
		private Random random = new Random();
		private BlockingQueue<PhotoFace> queue;
		private AtomicBoolean done;
		private CountDownLatch begin;
		private CountDownLatch end;
		
		private UploadWorker(CountDownLatch _begin, CountDownLatch _end, 
				BlockingQueue<PhotoFace> _queue, AtomicBoolean _done) {
			this.begin = _begin;
			this.end = _end;
			this.queue = _queue;
			this.done = _done;
		}

		@Override
		public void run() {
			try {
				begin.await();
				File[] pictures = UploadUtils.getPictures();
				for(int i = 0; i < pictures.length; i++) {
					File picture = pictures[i];
					byte[] body = FileUtils.read(picture);
					String uri = UploadUtils.createUri(picture.getName());
					Future<Response> result = uploader.asyncUpload(uri, body);
					int statusCode = result.get().getStatusCode();
					assertTrue("文件" + uri + "上传失败", (statusCode >= 200) && (statusCode < 300));
					logger.debug("文件{}上传成功", uri);
					Thread.sleep(random.nextInt(10) * 10 * AsyncUploadAndDownloadTest.this.threadQuantity);
					queue.offer(new PhotoFace(uri, ComparsionUtils.digest(body)));
				}
				
				done.compareAndSet(false, true);
				end.countDown();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	private class DownloadWorker implements Runnable {
		private Random random = new Random();
		private BlockingQueue<PhotoFace> queue;
		private CountDownLatch begin;
		private CountDownLatch end;
		private AtomicBoolean done;
		
		private DownloadWorker(CountDownLatch _begin, CountDownLatch _end,
				BlockingQueue<PhotoFace> _queue, AtomicBoolean _done) {
			this.begin = _begin;
			this.end = _end;
			this.queue = _queue;
			this.done = _done;
		}
		
		@Override
		public void run() {
			try {
				begin.await();
				PhotoFace photo = null;
				while(!done.get()) {
					if((photo = queue.poll(20, TimeUnit.MILLISECONDS)) != null) {
						Future<Response> result = downloader.asyncDownload(photo.getUri());
						byte[] body = result.get().getBody();
						assertEquals("文件" + photo.getUri() + "下载失败", photo.getDigest(), ComparsionUtils.digest(body));
						logger.debug("文件{}下载成功, 下载{}字节", photo.getUri(), body.length);
					}
					Thread.sleep(random.nextInt(10) * 10 * AsyncUploadAndDownloadTest.this.threadQuantity);
				}
				
				end.countDown();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
}
