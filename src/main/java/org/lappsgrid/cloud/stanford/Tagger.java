package org.lappsgrid.cloud.stanford;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public class Tagger extends BaseService
{
	public Tagger()
	{

	}

	@Override
	protected String[] produces()
	{
		return new String[] {Uri.SENTENCE, Uri.TOKEN, Uri.POS };
	}

	@Override
	protected String pipeline()
	{
		return "pos";
	}
}
