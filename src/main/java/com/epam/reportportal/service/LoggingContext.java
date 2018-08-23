/*
 * Copyright (C) 2018 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.service;

import com.epam.reportportal.message.TypeAwareByteSource;
import com.epam.reportportal.restendpoint.http.MultiPartRequest;
import com.epam.ta.reportportal.ws.model.BatchSaveOperatingRS;
import com.epam.ta.reportportal.ws.model.Constants;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import org.reactivestreams.Publisher;

import java.util.List;

import static com.epam.reportportal.utils.SubscriptionUtils.*;
import static com.epam.reportportal.utils.files.ImageConverter.convert;
import static com.epam.reportportal.utils.files.ImageConverter.isImage;
import static com.google.common.io.ByteSource.wrap;

/**
 * Logging context holds thread-local context for logging and converts
 * {@link SaveLogRQ} to multipart HTTP request to ReportPortal
 * Basic flow:
 * After start some test item (suite/test/step) context should be initialized with observable of
 * item ID and ReportPortal client.
 * Before actual finish of test item, context should be closed/completed.
 * Context consists of {@link Flowable} with buffering back-pressure strategy to be able
 * to batch incoming log messages into one request
 *
 * @author Andrei Varabyeu
 * @see #init(Maybe, ReportPortalClient)
 */
public class LoggingContext {

	/* default back-pressure buffer size */
	public static final int DEFAULT_BUFFER_SIZE = 10;

    // switch to inheritable thread local to support TestNG 6.11+
	static final ThreadLocal<LoggingContext> CONTEXT_THREAD_LOCAL = new InheritableThreadLocal<LoggingContext>();

	/**
	 * Initializes new logging context and attaches it to current thread
	 *
	 * @param itemId Test Item ID
	 * @param client Client of ReportPortal
	 * @return New Logging Context
	 */
	public static LoggingContext init(Maybe<String> itemId, final ReportPortalClient client) {
		return init(itemId, client, DEFAULT_BUFFER_SIZE, false);
	}

	/**
	 * Initializes new logging context and attaches it to current thread
	 *
	 * @param itemId        Test Item ID
	 * @param client        Client of ReportPortal
	 * @param bufferSize    Size of back-pressure buffer
	 * @param convertImages Whether Image should be converted to BlackAndWhite
	 * @return New Logging Context
	 */
	public static LoggingContext init(Maybe<String> itemId, final ReportPortalClient client, int bufferSize, boolean convertImages) {
		LoggingContext context = new LoggingContext(itemId, client, bufferSize, convertImages);
		CONTEXT_THREAD_LOCAL.set(context);
		return context;
	}

	/**
	 * Completes context attached to the current thread
	 *
	 * @return Waiting queue to be able to track request sending completion
	 */
	public static Completable complete() {
		final LoggingContext loggingContext = CONTEXT_THREAD_LOCAL.get();
		if (null != loggingContext) {
			return loggingContext.completed();
		} else {
			return Maybe.empty().ignoreElement();
		}
	}

	/* Log emitter */
	private final PublishSubject<Maybe<SaveLogRQ>> emitter;
	/* ID of TestItem in ReportPortal */
	private final Maybe<String> itemId;
	/* Whether Image should be converted to BlackAndWhite */
	private final boolean convertImages;

	LoggingContext(Maybe<String> itemId, final ReportPortalClient client, int bufferSize, boolean convertImages) {
		this.itemId = itemId;
		this.emitter = PublishSubject.create();
		this.convertImages = convertImages;
		emitter.toFlowable(BackpressureStrategy.BUFFER)
				.flatMap(new Function<Maybe<SaveLogRQ>, Publisher<SaveLogRQ>>() {
					@Override
					public Publisher<SaveLogRQ> apply(Maybe<SaveLogRQ> rq) throws Exception {
						return rq.toFlowable();
					}
				})
				.buffer(bufferSize)
				.flatMap(new Function<List<SaveLogRQ>, Flowable<BatchSaveOperatingRS>>() {
					@Override
					public Flowable<BatchSaveOperatingRS> apply(List<SaveLogRQ> rqs) throws Exception {
						MultiPartRequest.Builder builder = new MultiPartRequest.Builder();

						builder.addSerializedPart(Constants.LOG_REQUEST_JSON_PART, rqs);

						for (SaveLogRQ rq : rqs) {
							final SaveLogRQ.File file = rq.getFile();
							if (null != file) {
								builder.addBinaryPart(Constants.LOG_REQUEST_BINARY_PART,
										file.getName(),
										Strings.isNullOrEmpty(file.getContentType()) ?
												MediaType.OCTET_STREAM.toString() :
												file.getContentType(),
										wrap(file.getContent())
								);
							}
						}
						return client.log(builder.build()).toFlowable();
					}
				})
				.doOnError(new Consumer<Throwable>() {
					@Override
					public void accept(Throwable throwable) throws Exception {
						throwable.printStackTrace();
					}
				})
				.observeOn(Schedulers.computation())
				.subscribe(logFlowableResults("Logging context"));

	}

	/**
	 * Emits log. Basically, put it into processing pipeline
	 *
	 * @param logSupplier Log Message Factory. Key if the function is actual test item ID
	 */
	public void emit(final com.google.common.base.Function<String, SaveLogRQ> logSupplier) {
		emitter.onNext(itemId.map(new Function<String, SaveLogRQ>() {
			@Override
			public SaveLogRQ apply(String input) throws Exception {
				final SaveLogRQ rq = logSupplier.apply(input);
				SaveLogRQ.File file = rq.getFile();
				if (convertImages && null != file && isImage(file.getContentType())) {
					final TypeAwareByteSource source = convert(wrap(file.getContent()));
					file.setContent(source.read());
					file.setContentType(source.getMediaType());
				}
				return rq;
			}
		}));

	}

	/**
	 * Marks flow as completed
	 *
	 * @return {@link Completable}
	 */
	public Completable completed() {
		emitter.onComplete();
		return emitter.ignoreElements();
	}

}
