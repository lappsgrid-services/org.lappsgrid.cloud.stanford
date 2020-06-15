package org.lappsgrid.cloud.stanford;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lappsgrid.api.WebService;
import org.lappsgrid.discriminator.Discriminators;
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

/**
 *
 */
public class NamedEntityRecognizerTest
{
	WebService service;

	@Before
	public void setup() {
		service = new NamedEntityRecognizer();
	}

	@After
	public void teardown() {
		service = null;
	}

	@Test
	public void metadata() {
		String json = service.getMetadata();
		Data data = Serializer.parse(json);
		assert Discriminators.Uri.META.equals(data.getDiscriminator());
		ServiceMetadata metadata = new ServiceMetadata((Map) data.getPayload());
		assert Version.getVersion().equals(metadata.getVersion());
		assert "3.9.2".equals(metadata.getToolVersion());
		assert "http://www.lappsgrid.org".equals(metadata.getVendor());
		assert Discriminators.Uri.GPL3.equals(metadata.getLicense());
		assert Discriminators.Uri.ALL.equals(metadata.getAllow());

		IOSpecification spec = metadata.getProduces();
		List<String> strings = spec.getFormat();
		assert 1 == strings.size();
		assert Discriminators.Uri.LIF.equals(strings.get(0));
		strings = spec.getAnnotations();
		assert 5 == strings.size();
		assert strings.contains(Discriminators.Uri.TOKEN);
		assert strings.contains(Discriminators.Uri.SENTENCE);
		assert strings.contains(Discriminators.Uri.POS);
		assert strings.contains(Discriminators.Uri.LEMMA);
		assert strings.contains(Discriminators.Uri.NE);

		spec = metadata.getRequires();
		strings = spec.getFormat();
		assert 2 == strings.size();
		assert strings.contains(Discriminators.Uri.LIF);
		assert strings.contains(Discriminators.Uri.TEXT);
	}

	@Test
	public void execute() {
		Container container = new Container();
		container.setText("Karen flew to New York. Nancy flew to Bloomington.");
		container.setLanguage("en");
		Data data = new Data(Discriminators.Uri.LIF, container);

		String json = service.execute(data.asJson());
		data = Serializer.parse(json);
		assert Discriminators.Uri.LIF.equals(data.getDiscriminator());

		container = new Container(data.getPayload());

		List<View> views = container.getViews();
		assert 3 == views.size();

		views = container.findViewsThatContain(Discriminators.Uri.TOKEN);
		assert 1 == views.size();
		assert 11 == views.get(0).getAnnotations().size();

		views = container.findViewsThatContain(Discriminators.Uri.SENTENCE);
		assert 1 == views.size();
		assert 2 == views.get(0).getAnnotations().size();

		views = container.findViewsThatContain(Discriminators.Uri.POS);
		assert 1 == views.size();
		List<Annotation> annotations = views.get(0).getAnnotations();
		assert 11 == annotations.size();
		assert 11 == annotations.stream().filter(a -> a.getFeature(Features.Token.POS) != null).count();

		views = container.findViewsThatContain(Discriminators.Uri.LEMMA);
		assert 1 == views.size();
		annotations = views.get(0).getAnnotations();
		assert 11 == annotations.size();
		assert 11 == annotations.stream().filter(a -> a.getFeature(Features.Token.POS) != null).count();

		views = container.findViewsThatContain(Discriminators.Uri.NE);
		assert 1 == views.size();
		annotations = views.get(0).getAnnotations();
		assert 4 == annotations.size();
		assert 4 == annotations.stream().filter(a -> a.getAtType().equals(Discriminators.Uri.NE)).count();

		//		System.out.println(data.asPrettyJson());

	}
}
