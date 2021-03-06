package net.threeple.pg.api;

import java.util.concurrent.Future;

import net.threeple.pg.api.model.Response;

public interface AsyncUploader {
	public Future<Response> asyncUpload(String uri, byte[] body);
}
