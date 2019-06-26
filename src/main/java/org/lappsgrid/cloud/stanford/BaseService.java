package org.lappsgrid.cloud.stanford;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import groovy.lang.MetaClass;
import org.lappsgrid.api.WebService;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.metadata.ServiceMetadataBuilder;
import org.lappsgrid.rabbitmq.Message;
import org.lappsgrid.rabbitmq.RabbitMQ;
import org.lappsgrid.rabbitmq.topic.PostOffice;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Container;

import java.io.IOException;
import java.util.UUID;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public abstract class BaseService implements WebService
{
	static final String HOST = "rabbitmq.lappsgrid.org/nlp";
	static final String POSTOFFICE = "distributed.nlp.stanford";
	static final String STANFORD = "pipelines";

	static final String SEGMENTER = "segmenter";
	static final String POS = "pos";
	static final String LEMMAS = "lemmas";
	static final String NER = "ner";

	private String metadata;

	private String username;
	private String password;

	public BaseService()
	{
		username = System.getenv("RABBIT_USERNAME");
		password = System.getenv("RABBIT_PASSWORD");
	}

	@Override
	public String execute(final String json)
	{
		Data data = Serializer.parse(json);
		String discriminator = data.getDiscriminator();
		if (Uri.ERROR.equals(discriminator)) {
			return json;
		}
		if (Uri.TEXT.equals(discriminator)) {
			String text = data.getPayload().toString();
			Container container = new Container();
			container.setText(text);
			container.setLanguage("en");
			data.setDiscriminator(Uri.LIF);
			data.setPayload(container);
		}
		else if (!Uri.LIF.equals(discriminator))
		{
			data.setDiscriminator(Uri.ERROR);
			data.setPayload("Invalid discriminator type: " + discriminator);
			return data.asPrettyJson();
		}

		Object semaphore = new Object();
		String response = null;
		String mbox = UUID.randomUUID().toString();
		PostOffice po = new PostOffice(POSTOFFICE, HOST);
		Box box = null;
		try
		{
			box = new Box(semaphore, mbox);
		}
		catch (IOException e)
		{
			data.setDiscriminator(Uri.ERROR);
			data.setPayload(e.getMessage());
			return data.asPrettyJson();
		}

		Message message = new Message()
				.body(data.asJson())
				.command(pipeline())
				.route(STANFORD, mbox);

		po.send(message);
		synchronized (semaphore) {
			try
			{
				semaphore.wait();
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}
		box.close();
		po.close();

		return box.getResponse();
//		System.out.println(s);
//		data = Serializer.parse(s);
//		data.setPayload(container);
//		return data.asPrettyJson();
	}

	@Override
	public String getMetadata()
	{
		if (metadata == null) {
			initMetadata();
		}
		return metadata;
	}

	abstract protected String[] produces();
	abstract protected String pipeline();

	private synchronized void initMetadata()
	{
		if (metadata != null) {
			return;
		}

		ServiceMetadata md = new ServiceMetadataBuilder()
				.name("Distributed Stanford NLP")
				.description("Pipelines from Stanford Core NLP")
				.vendor("http://www.lappsgrid.org")
				.version(Version.getVersion())
				.toolVersion("3.9.1")
				.requireFormats(Uri.TEXT, Uri.LIF)
				.produceFormat(Uri.LIF)
				.license(Uri.GPL3)
				.produces(produces())
				.allow(Uri.ALL)
				.build();

		metadata = new Data(Uri.META, md).asPrettyJson();
	}

	/**
	 * Due to a bug in Groovy we can not extend the org.lappsgrid.rabbitmq.topic.Mailbox
	 * class. See https://issues.apache.org/jira/browse/GROOVY-7362
	 *
	 * The bug is fixed in Groovy 3.0, but as of this writing (June 25, 2019) Groovy 3
	 * has not been released yet.
	 */
	class Box extends RabbitMQ
	{
		private String exchange;
		private String response;
		private Object semaphore;

		public Box(Object semaphore, String address) throws IOException
		{
			super("", HOST);
			this.semaphore = semaphore;
//		}
//
//		public Box(String exchange, String address, String host) throws IOException
//		{
			getChannel().exchangeDeclare(POSTOFFICE, "direct");
			boolean passive = false;
			boolean durable = true;
			boolean exclusive = false;
			boolean autoDelete = true;
			Channel channel = this.getChannel();
			String qName = channel.queueDeclare("", durable, exclusive, autoDelete, null).getQueue();
			this.setQueueName(qName);
			channel.queueBind(qName, POSTOFFICE, address);
			channel.basicConsume(qName, false, new MailBoxConsumer(this));
		}

		void recv(String s)
		{
			Message message = Serializer.parse(s, Message.class);
			response = message.getBody().toString();
			synchronized (semaphore) {
				semaphore.notify();
			}
		}

		public String getResponse() {
			return this.response;
		}

		@Override
		public Object invokeMethod(String s, Object o)
		{
			return null;
		}

		@Override
		public Object getProperty(String s)
		{
			return null;
		}

		@Override
		public void setProperty(String s, Object o)
		{

		}

		@Override
		public MetaClass getMetaClass()
		{
			return null;
		}

		@Override
		public void setMetaClass(MetaClass metaClass)
		{

		}

		class MailBoxConsumer extends DefaultConsumer
		{
			Box box;

			MailBoxConsumer(Box box) {
				super(box.getChannel());
				this.box = box;
			}

			public void handleDelivery(String consumerTag, Envelope envelope,
								AMQP.BasicProperties properties, byte[] body)
					throws IOException
			{
				String message = new String(body, "UTF-8");
				box.recv(message);
				getChannel().basicAck(envelope.getDeliveryTag(), false);
			}

		}

	}
}
