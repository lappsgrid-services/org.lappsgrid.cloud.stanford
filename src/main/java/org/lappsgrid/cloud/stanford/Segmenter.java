package org.lappsgrid.cloud.stanford;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public class Segmenter extends BaseService
{
	@Override
	protected String[] produces()
	{
		return new String[] { Uri.TOKEN, Uri.SENTENCE };
	}

	@Override
	protected String pipeline()
	{
		return "segmenter";
	}
}
