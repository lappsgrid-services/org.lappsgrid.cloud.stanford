package org.lappsgrid.cloud.stanford;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public class Lemmatizer extends BaseService
{
	@Override
	protected String[] produces()
	{
		return new String[] { Uri.SENTENCE, Uri.TOKEN, Uri.POS, Uri.LEMMA } ;
	}

	@Override
	protected String pipeline()
	{
		return "lemmas";
	}
}
