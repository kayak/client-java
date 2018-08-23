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

import static com.epam.reportportal.restendpoint.http.HttpMethod.POST;
import static com.epam.reportportal.restendpoint.http.HttpMethod.PUT;

import com.epam.reportportal.restendpoint.http.MultiPartRequest;
import com.epam.reportportal.restendpoint.http.annotation.Body;
import com.epam.reportportal.restendpoint.http.annotation.Close;
import com.epam.reportportal.restendpoint.http.annotation.Multipart;
import com.epam.reportportal.restendpoint.http.annotation.Path;
import com.epam.reportportal.restendpoint.http.annotation.Request;
import com.epam.ta.reportportal.ws.model.BatchSaveOperatingRS;
import com.epam.ta.reportportal.ws.model.EntryCreatedRS;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.reactivex.Maybe;

/**
 * @author Andrei Varabyeu
 */
public interface ReportPortalClient {

	@Request(method = POST, url = "/launch")
	Maybe<StartLaunchRS> startLaunch(@Body StartLaunchRQ rq);

	@Request(method = PUT, url = "/launch/{launchId}/finish")
	Maybe<OperationCompletionRS> finishLaunch(@Path("launchId") String launch, @Body FinishExecutionRQ rq);

	@Request(method = PUT, url = "/launch/{launchId}/stop")
	Maybe<OperationCompletionRS> stopLaunch(@Path("launchId") String launch, @Body FinishExecutionRQ rq);

	@Request(method = POST, url = "/item/")
	Maybe<ItemCreatedRS> startTestItem(@Body StartTestItemRQ rq);

	@Request(method = POST, url = "/item/{parent}")
	Maybe<ItemCreatedRS> startTestItem(@Path("parent") String parent, @Body StartTestItemRQ rq);

	@Request(method = PUT, url = "/item/{itemId}")
	Maybe<OperationCompletionRS> finishTestItem(@Path("itemId") String itemId, @Body FinishTestItemRQ rq);

	@Request(method = POST, url = "/log/")
	Maybe<EntryCreatedRS> log(@Body SaveLogRQ rq);

	@Request(method = POST, url = "/log/")
	Maybe<BatchSaveOperatingRS> log(@Body @Multipart MultiPartRequest rq);

	@Close
	void close();
}
