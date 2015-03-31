package org.openmrs.module.openconceptlab.updater;

import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.apache.commons.io.IOUtils;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.module.openconceptlab.Item;
import org.openmrs.module.openconceptlab.ItemState;
import org.openmrs.module.openconceptlab.MockTest;
import org.openmrs.module.openconceptlab.Subscription;
import org.openmrs.module.openconceptlab.TestResources;
import org.openmrs.module.openconceptlab.Update;
import org.openmrs.module.openconceptlab.UpdateService;
import org.openmrs.module.openconceptlab.client.OclClient;
import org.openmrs.module.openconceptlab.client.OclMapping;
import org.openmrs.module.openconceptlab.client.OclClient.OclResponse;
import org.openmrs.module.openconceptlab.client.OclConcept;

public class UpdaterTest extends MockTest {
	
	@Mock
	OclClient oclClient;
	
	@Mock
	UpdateService updateService;
	
	@Mock
	Importer importer;
	
	@InjectMocks
	Updater updater;
	
	/**
	 * @see Updater#run()
	 * @verifies start first update with response date
	 */
	@Test
	public void runUpdate_shouldStartFirstUpdateWithResponseDate() throws Exception {
		Subscription subscription = new Subscription();
		subscription.setUrl("http://some.com/url");
		when(updateService.getSubscription()).thenReturn(subscription);
		
		Date updatedTo = new Date();
		OclResponse oclResponse = new OclClient.OclResponse(IOUtils.toInputStream("{}"), 0, updatedTo);
		when(updateService.getLastUpdate()).thenReturn(null);
		when(oclClient.fetchUpdates(subscription.getUrl(), subscription.getToken(), null)).thenReturn(oclResponse);
		
		updater.run();
		
		verify(updateService).updateOclDateStarted(Mockito.any(Update.class), Mockito.eq(updatedTo));
	}
	
	/**
	 * @see Updater#run()
	 * @verifies start next update with updated since
	 */
	@Test
	public void runUpdate_shouldStartNextUpdateWithUpdatedSince() throws Exception {
		Subscription subscription = new Subscription();
		subscription.setUrl("http://some.com/url");
		when(updateService.getSubscription()).thenReturn(subscription);
		
		Update lastUpdate = new Update();
		Date updatedSince = new Date();
		lastUpdate.setOclDateStarted(updatedSince);
		when(updateService.getLastSuccessfulUpdate()).thenReturn(lastUpdate);
		
		Date updatedTo = new Date();
		OclResponse oclResponse = new OclClient.OclResponse(IOUtils.toInputStream("{}"), 0, updatedTo);
		when(oclClient.fetchUpdates(subscription.getUrl(), subscription.getToken(), lastUpdate.getOclDateStarted())).thenReturn(oclResponse);
		
		updater.run();
		
		verify(updateService).updateOclDateStarted(Mockito.any(Update.class), Mockito.eq(updatedTo));
	}
	
	/**
	 * @see Updater#run()
	 * @verifies create item for each concept and mapping
	 */
	@Test
	public void runUpdate_shouldCreateItemForEachConceptAndMapping() throws Exception {
		Subscription subscription = new Subscription();
		subscription.setUrl("http://some.com/url");
		when(updateService.getSubscription()).thenReturn(subscription);
		
		Update lastUpdate = new Update();
		Date updatedSince = new Date();
		lastUpdate.setOclDateStarted(updatedSince);
		
		when(updateService.getLastSuccessfulUpdate()).thenReturn(lastUpdate);
		
		Date updatedTo = new Date();
		OclResponse oclResponse = new OclClient().unzipResponse(TestResources.getSimpleResponseAsStream(), updatedTo);
		
		when(oclClient.fetchUpdates(subscription.getUrl(), subscription.getToken(), lastUpdate.getOclDateStarted())).thenReturn(oclResponse);
		
		doAnswer(new Answer<Item>() {

			@Override
            public Item answer(InvocationOnMock invocation) throws Throwable {
				Update update = (Update) invocation.getArguments()[0];
				OclConcept oclConcept = (OclConcept) invocation.getArguments()[1];
	            return new Item(update, oclConcept, ItemState.ADDED);
            }}).when(importer).importItem(any(Update.class), any(OclConcept.class));
		
		doAnswer(new Answer<Item>() {

			@Override
            public Item answer(InvocationOnMock invocation) throws Throwable {
	            Update update = (Update) invocation.getArguments()[0];
	            OclMapping oclMapping = (OclMapping) invocation.getArguments()[1];
	            return new Item(update, oclMapping, ItemState.ADDED);
            }
			
		}).when(importer).importItem(any(Update.class), any(OclMapping.class));
		
		updater.run();
		
		//concepts must be saved fist
		InOrder inOrder = inOrder(updateService);
		
		//concepts
		inOrder.verify(updateService).saveItem(argThat(hasUuid("1001AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")));
		inOrder.verify(updateService).saveItem(argThat(hasUuid("1002AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")));
		inOrder.verify(updateService).saveItem(argThat(hasUuid("1003AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")));
		
		//mappings
		inOrder.verify(updateService).saveItem(argThat(hasUuid("697bf112-a7ca-3ae3-af4f-8b46e3af7f10")));
		inOrder.verify(updateService).saveItem(argThat(hasUuid("def16c32-0635-3afd-8a56-a080830e2bff")));
		inOrder.verify(updateService).saveItem(argThat(hasUuid("b705416c-ad04-356f-9d43-8945ee382722")));
	}
	
	public Matcher<Update> hasOclDateStarted(Date oclDateStarted) {
		return new FeatureMatcher<Update, Date>(
		                                        is(oclDateStarted), "oclDateStarted", "oclDateStarted") {
			
			@Override
			protected Date featureValueOf(Update actual) {
				return actual.getOclDateStarted();
			}
		};
	}
	
	public Matcher<Item> hasUuid(String uuid) {
		return new FeatureMatcher<Item, String>(
		                                        is(uuid), "uuid", "uuid") {
			
			@Override
			protected String featureValueOf(Item actual) {
				return actual.getUuid();
			}
		};
	}
}
