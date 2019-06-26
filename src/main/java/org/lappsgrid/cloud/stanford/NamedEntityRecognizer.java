package org.lappsgrid.cloud.stanford;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public class NamedEntityRecognizer extends BaseService
{
	public NamedEntityRecognizer()
	{

	}

	@Override
	protected String[] produces()
	{
		return new String[] { Uri.SENTENCE, Uri.TOKEN, Uri.POS, Uri.LEMMA, Uri.NE };
	}

	@Override
	protected String pipeline()
	{
		return "ner";
	}
}
