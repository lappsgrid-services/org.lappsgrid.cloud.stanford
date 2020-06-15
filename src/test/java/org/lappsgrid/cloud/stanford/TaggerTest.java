package org.lappsgrid.cloud.stanford;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lappsgrid.api.WebService;
import org.lappsgrid.metadata.IOSpecification;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Annotation;
import org.lappsgrid.serialization.lif.Container;
import org.lappsgrid.serialization.lif.View;
import org.lappsgrid.vocabulary.Features;

import java.util.List;
import java.util.Map;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public class TaggerTest
{
	WebService service;

	@Before
	public void setup() {
		service = new Tagger();
	}

	@After
	public void teardown() {
		service = null;
	}

	@Test
	public void metadata() {
		String json = service.getMetadata();
		Data data = Serializer.parse(json);
		assert Uri.META.equals(data.getDiscriminator());
		ServiceMetadata metadata = new ServiceMetadata((Map) data.getPayload());
		assert Version.getVersion().equals(metadata.getVersion());
		assert "3.9.2".equals(metadata.getToolVersion());
		assert "http://www.lappsgrid.org".equals(metadata.getVendor());
		assert Uri.GPL3.equals(metadata.getLicense());
		assert Uri.ALL.equals(metadata.getAllow());

		IOSpecification spec = metadata.getProduces();
		List<String> strings = spec.getFormat();
		assert 1 == strings.size();
		assert Uri.LIF.equals(strings.get(0));
		strings = spec.getAnnotations();
		assert 3 == strings.size();
		assert strings.contains(Uri.TOKEN);
		assert strings.contains(Uri.SENTENCE);
		assert strings.contains(Uri.POS);
		
		spec = metadata.getRequires();
		strings = spec.getFormat();
		assert 2 == strings.size();
		assert strings.contains(Uri.LIF);
		assert strings.contains(Uri.TEXT);
	}

	@Test
	public void execute() {
		Container container = new Container();
		container.setText("Karen flew to New York. Nancy flew to Bloomington.");
		container.setLanguage("en");
		Data data = new Data(Uri.LIF, container);

		String json = service.execute(data.asJson());
		data = Serializer.parse(json);
		System.out.println(data.asPrettyJson());
		assert Uri.LIF.equals(data.getDiscriminator());

		container = new Container(data.getPayload());

		List<View> views = container.getViews();
		assert 2 == views.size();

		views = container.findViewsThatContain(Uri.TOKEN);
		assert 1 == views.size();
		assert 11 == views.get(0).getAnnotations().size();

		views = container.findViewsThatContain(Uri.SENTENCE);
		assert 1 == views.size();
		assert 2 == views.get(0).getAnnotations().size();

		views = container.findViewsThatContain(Uri.POS);
		System.out.println(views.size());
		assert 1 == views.size();
		List<Annotation> annotations = views.get(0).getAnnotations();
		assert 11 == annotations.size();

		assert 11 == annotations.stream().filter(a -> a.getFeature(Features.Token.POS) != null).count();

		System.out.println(data.asPrettyJson());

	}
}
