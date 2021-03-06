/* 
 * Copyright (C) 2015 Jeremy Wildsmith.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package io.github.jevaengine.rpg.ui;

import io.github.jevaengine.IDisposable;
import io.github.jevaengine.graphics.IRenderable;
import io.github.jevaengine.math.Rect2D;
import io.github.jevaengine.math.Vector2D;
import io.github.jevaengine.math.Vector3F;
import io.github.jevaengine.rpg.dialogue.DialogueSession.DialogueQuery;
import io.github.jevaengine.rpg.dialogue.IDialogueListenerSession;
import io.github.jevaengine.rpg.dialogue.IDialogueListenerSession.IDialogueListenerSessionObserver;
import io.github.jevaengine.ui.Button;
import io.github.jevaengine.ui.Button.IButtonPressObserver;
import io.github.jevaengine.ui.*;
import io.github.jevaengine.ui.IWindowFactory.WindowConstructionException;
import io.github.jevaengine.ui.TextArea;
import io.github.jevaengine.ui.Window;
import io.github.jevaengine.util.IObserverRegistry;
import io.github.jevaengine.util.Observers;
import io.github.jevaengine.world.scene.ISceneBuffer;
import io.github.jevaengine.world.scene.ISceneBufferFactory;

import java.awt.*;
import java.net.URI;

public final class CharacterDialogueQueryFactory
{
	private final URI m_characterDialogueLayout;
	
	private final WindowManager m_windowManager;
	private final IWindowFactory m_windowFactory;
	
	private final ISceneBufferFactory m_speakerViewSceneBufferFactory;
	
	public CharacterDialogueQueryFactory(WindowManager windowManager, IWindowFactory windowFactory, URI characterDialogueLayout, ISceneBufferFactory speakerViewSceneBufferFactory)
	{
		m_windowManager = windowManager;
		m_windowFactory = windowFactory;
		m_characterDialogueLayout = characterDialogueLayout;
		m_speakerViewSceneBufferFactory = speakerViewSceneBufferFactory;
	}
	
	public CharacterDialogueQuery create(IDialogueListenerSession session) throws WindowConstructionException
	{
		Observers observers = new Observers();
		
		Window window = m_windowFactory.create(m_characterDialogueLayout, new CharacterDialogueQueryBehaviourInjector(observers, session));
		m_windowManager.addWindow(window);

		window.center();
		
		return new CharacterDialogueQuery(observers, window);
	}
	
	public static class CharacterDialogueQuery implements IDisposable
	{
		private final Observers m_observers;
		private final Window m_window;
		
		private CharacterDialogueQuery(Observers observers, Window window)
		{
			m_observers = observers;
			m_window = window;
		}
		
		@Override
		public void dispose()
		{
			m_window.dispose();
		}
		
		public void setVisible(boolean isVisible)
		{
			m_window.setVisible(isVisible);
		}
		
		public void setLocation(Vector2D location)
		{
			m_window.setLocation(location);
		}
		
		public void center()
		{
			m_window.center();
		}
		
		public void focus()
		{
			m_window.focus();
		}
		
		public void setMovable(boolean isMovable)
		{
			m_window.setMovable(false);
		}
		
		public IObserverRegistry getObservers()
		{
			return m_observers;
		}
		
		public Rect2D getBounds()
		{
			return m_window.getBounds();
		}
	}

	public interface ICharacterDialogueQuerySessionObserver
	{
		void sessionEnded();
	}
	
	private class CharacterDialogueQueryBehaviourInjector extends WindowBehaviourInjector
	{
		private final Observers m_observers;
		private final IDialogueListenerSession m_listenerSession;
		
		private String[] m_currentAnswers = new String[0];
		private int m_answersOffset = 0;
		
		public CharacterDialogueQueryBehaviourInjector(Observers observers, IDialogueListenerSession listenerSession)
		{
			m_observers = observers;
			m_listenerSession = listenerSession;
		}
		
		@Override
		protected void doInject() throws NoSuchControlException
		{
			final TextArea txtQuery = getControl(TextArea.class, "txtQuery");
			final Viewport speakerView = getControl(Viewport.class, "speakerView");
			
			final Button[] btnAnswers = new Button[] {
					getControl(Button.class, "btnAnswer0"),
					getControl(Button.class, "btnAnswer1"),
					getControl(Button.class, "btnAnswer2"),
					getControl(Button.class, "btnAnswer3"),
			};
			
			final Button btnMoreAnswers = getControl(Button.class, "btnMoreAnswer");
			
			btnMoreAnswers.setVisible(false);
			
			for(final Button b : btnAnswers)
			{
				b.setVisible(false);
				b.getObservers().add(new IButtonPressObserver() {
					
					@Override
					public void onPress() {
						m_listenerSession.listenerSay(b.getText());
					}
				});
			}
			
			btnMoreAnswers.getObservers().add(new IButtonPressObserver() {
				@Override
				public void onPress()
				{
					m_answersOffset = (m_answersOffset + btnAnswers.length) % m_currentAnswers.length;
					
					for(int i = 0; i < btnAnswers.length; i++)
					{
						Button answerButton = btnAnswers[i];
					
						if(m_answersOffset + i < m_currentAnswers.length)
						{
							answerButton.setText(m_currentAnswers[m_answersOffset + i]);
							answerButton.setVisible(true);
						}else
							answerButton.setVisible(false);
					}
				}
			});
			
			final ISceneBuffer speakerSceneBuffer = m_speakerViewSceneBufferFactory.create();
			
			IDialogueListenerSessionObserver uiConfigure = new IDialogueListenerSessionObserver() {

				@Override
				public void speakerInquired(final DialogueQuery query)
				{
					speakerView.setView(new IRenderable() {
						@Override
						public void render(Graphics2D g, int x, int y, float scale)
						{
							speakerSceneBuffer.reset();
							speakerSceneBuffer.addModel(query.getSpeaker().getModel(), new Vector3F());
							speakerSceneBuffer.render(g, x + speakerView.getBounds().width / 2, y + speakerView.getBounds().height / 2, scale, speakerView.getBounds());
						}
					});
					
					txtQuery.setText(query.getQuery());
					
					m_currentAnswers = query.getAnswers();
					m_answersOffset = 0;
					
					btnMoreAnswers.setVisible(m_currentAnswers.length > btnAnswers.length);
					
					for(int i = 0; i < btnAnswers.length; i++)
					{
						if(i < m_currentAnswers.length)
						{
							btnAnswers[i].setText(m_currentAnswers[i]);
							btnAnswers[i].setVisible(true);
						}else
							btnAnswers[i].setVisible(false);
					}
				}

				@Override
				public void end()
				{
					m_answersOffset = 0;
					m_currentAnswers = new String[0];
					m_observers.raise(ICharacterDialogueQuerySessionObserver.class).sessionEnded();
				}
			};
			
			m_listenerSession.getObservers().add(uiConfigure);
			
			DialogueQuery currentQuery = m_listenerSession.getCurrentQuery();
			
			if(currentQuery == null)
				uiConfigure.end();
			else
				uiConfigure.speakerInquired(currentQuery);
		}
	}
}