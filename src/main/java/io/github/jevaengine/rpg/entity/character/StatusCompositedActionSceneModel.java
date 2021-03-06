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
package io.github.jevaengine.rpg.entity.character;

import io.github.jevaengine.IDisposable;
import io.github.jevaengine.math.Matrix3X3;
import io.github.jevaengine.math.Rect2D;
import io.github.jevaengine.math.Rect3F;
import io.github.jevaengine.math.Vector3F;
import io.github.jevaengine.rpg.IImmutableAttributeSet.IAttributeChangeObserver;
import io.github.jevaengine.rpg.IImmutableAttributeSet.IImmutableAttribute;
import io.github.jevaengine.ui.Label;
import io.github.jevaengine.ui.ValueGuage;
import io.github.jevaengine.ui.style.IUIStyle;
import io.github.jevaengine.util.IObserverRegistry;
import io.github.jevaengine.world.Direction;
import io.github.jevaengine.world.physics.PhysicsBodyShape;
import io.github.jevaengine.world.scene.model.IActionSceneModel;
import io.github.jevaengine.world.scene.model.ISceneModel;
import io.github.jevaengine.world.scene.model.particle.IParticleEmitter;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Jeremy
 */
public class StatusCompositedActionSceneModel implements IActionSceneModel, IDisposable
{
	private static final int MESSAGE_LIFETIME = 3000;
	private static final int HEALTH_GUAGE_LIFETIME = 6000;
	private static final int BLEED_LIFETIME = 500;
	
	private final IImmutableAttribute m_healthAttribute;
	private final IImmutableAttribute m_maxHealthAttribute;
	private final HealthAttributeObserver m_healthObserver;
	
	private final List<IStatusSceneModelComponent> m_components = new ArrayList<>();
	private final IActionSceneModel m_baseModel;
	private final IParticleEmitter m_bloodEmitter;
	
	private final IUIStyle m_statusStyle;
	
	private final ValueGuage m_healthGuage;
	
	private int m_bleedLife = BLEED_LIFETIME;
	private int m_healthGuageLife = HEALTH_GUAGE_LIFETIME;
	
	public StatusCompositedActionSceneModel(IActionSceneModel baseModel, IUIStyle statusStyle, IParticleEmitter bloodEmitter, IImmutableAttribute healthAttribute, IImmutableAttribute maxHealthAttribute)
	{
		m_statusStyle = statusStyle;
		m_baseModel = baseModel;
		m_healthAttribute = healthAttribute;
		m_maxHealthAttribute = maxHealthAttribute;
		m_bloodEmitter = bloodEmitter;
	
		m_healthGuage = new ValueGuage(new Rect2D(70, 10));
		m_healthGuage.setStyle(statusStyle);
		
		m_components.add(new HealthGuageComponent());
		m_components.add(new BloodComponent(baseModel.getAABB()));
		
		m_healthObserver = new HealthAttributeObserver();
		m_healthAttribute.getObservers().add(m_healthObserver);
	}
	
	private void addStatusMessage(String message)
	{
		Label lbl = new Label(message);
		lbl.setStyle(m_statusStyle);
		StatusMessageComponent c = new StatusMessageComponent(lbl, m_baseModel.getAABB());
		m_components.add(c);
	}
	
	private void bleed()
	{
		m_bleedLife = 0;
		m_healthGuageLife = 0;
	}
	
	@Override
	public void dispose()
	{
		m_healthAttribute.getObservers().remove(m_healthObserver);
		m_baseModel.dispose();
		
		for(IStatusSceneModelComponent c : m_components)
			c.dispose();
		
		m_components.clear();
		m_healthGuage.dispose();
	}
	
	@Override
	public void update(int deltaTime)
	{
		if(m_bleedLife < BLEED_LIFETIME)
		{
			m_bloodEmitter.setEmit(true);
			m_bleedLife += deltaTime;
		}else
			m_bloodEmitter.setEmit(false);
		
		m_bloodEmitter.update(deltaTime);
		m_baseModel.update(deltaTime);
		
		if(!m_maxHealthAttribute.isZero())
			m_healthGuage.setValue(m_healthAttribute.get() / m_maxHealthAttribute.get());
		
		ListIterator<IStatusSceneModelComponent> it = m_components.listIterator();
		
		while(it.hasNext())
		{
			IStatusSceneModelComponent next = it.next();
			
			if(next.update(deltaTime))
			{
				it.remove();
				next.dispose();
			}
		}
	}

	@Override
	public Direction getDirection()
	{
		return m_baseModel.getDirection();
	}

	@Override
	public void setDirection(Direction direction)
	{
		m_baseModel.setDirection(direction);
	}

	@Override
	public IObserverRegistry getObservers() {
		return m_baseModel.getObservers();
	}

	@Override
	public Collection<ISceneModelComponent> getComponents(Matrix3X3 projection)
	{
		List<ISceneModelComponent> components = new ArrayList<>(m_baseModel.getComponents(projection));
		components.addAll(m_components);
		
		return components;
	}

	@Override
	public Rect3F getAABB()
	{
		return m_baseModel.getAABB();
	}

	@Override
	public PhysicsBodyShape getBodyShape()
	{
		return m_baseModel.getBodyShape();
	}
	
	@Override
	public ISceneModel clone()
	{
		return m_baseModel.clone();
	}

	@Override
	public IActionSceneModelAction getAction(String name)
	{
		return m_baseModel.getAction(name);
	}

	@Override
	public boolean hasAction(String name)
	{
		return m_baseModel.hasAction(name);
	}
	
	
	private interface IStatusSceneModelComponent extends ISceneModelComponent, IDisposable
	{
		boolean update(int deltaTime);
	}
	
	private final class HealthGuageComponent implements IStatusSceneModelComponent
	{
		@Override
		public void dispose() { }
		
		@Override
		public boolean update(int deltaTime)
		{
			if(m_healthGuageLife < HEALTH_GUAGE_LIFETIME)
				m_healthGuageLife += deltaTime;
			
			return false;
		}

		@Override
		public String getName()
		{
			return HealthGuageComponent.class.getName();
		}

		@Override
		public boolean testPick(int x, int y, float scale)
		{
			return false;
		}

		@Override
		public Rect3F getBounds()
		{
			Rect3F baseBounds = m_baseModel.getAABB();
			
			return new Rect3F(getOrigin(),
												baseBounds.width,
												baseBounds.height,
												0.1F);
		
		}

		@Override
		public Vector3F getOrigin()
		{
			Rect3F baseBounds = m_baseModel.getAABB();
			return new Vector3F(baseBounds.width / 2.0F,
												baseBounds.height / 2.0F - 0.1f, //Place it behing status messages.
												baseBounds.depth * 1.5F);
		
		}

		@Override
		public void render(Graphics2D g, int x, int y, float scale)
		{
			if(m_healthGuageLife < HEALTH_GUAGE_LIFETIME && !m_healthAttribute.isZero())
			{
				Rect2D healthBounds = m_healthGuage.getBounds();
				m_healthGuage.render(g, x - healthBounds.width / 2, y, scale);
			}
		}
	}
	
	private static final class BloodComponent implements IStatusSceneModelComponent
	{
		private final Rect3F m_baseAABB;
		
		public BloodComponent(Rect3F baseAABB)
		{
			m_baseAABB = baseAABB;
		}
		
		@Override
		public void dispose() { }
		
		@Override
		public boolean update(int deltaTime)
		{	
			return false;
		}

		@Override
		public String getName()
		{
			return BloodComponent.class.getName();
		}

		@Override
		public boolean testPick(int x, int y, float scale)
		{
			return false;
		}

		@Override
		public Rect3F getBounds()
		{
			return new Rect3F(m_baseAABB.getPoint(1.0F, 1.0F, 1.1F),
												m_baseAABB.width,
												m_baseAABB.height,
												0.1F);
		
		}

		@Override
		public Vector3F getOrigin()
		{
			return new Vector3F(m_baseAABB.width / 2.0F,
												m_baseAABB.height / 2.0F,
												m_baseAABB.depth * 0.5F);
		
		}

		@Override
		public void render(Graphics2D g, int x, int y, float scale)
		{
		//	m_bloodEmitter.render(g, x, y, scale);
		}
	}
	
	private static final class StatusMessageComponent implements IStatusSceneModelComponent, IDisposable
	{
		private static final AtomicInteger COUNT = new AtomicInteger();
		private final String m_name = getClass().getName() + COUNT.getAndIncrement();
		
		private final Label m_label;
		private int m_life = 0;
		
		private final Rect3F m_baseAABB;
		
		public StatusMessageComponent(Label label, Rect3F baseAABB)
		{
			m_label = label;
			m_baseAABB = new Rect3F(baseAABB);
		}

		@Override
		public void dispose()
		{
			m_label.dispose();
			m_life = MESSAGE_LIFETIME;
		}
		
		@Override
		public boolean update(int deltaTime)
		{
			m_life += deltaTime;
			
			if(m_life >= MESSAGE_LIFETIME)
			{
				m_label.dispose();
				return true;
			}else
				return false;
		}
		
		@Override
		public String getName()
		{
			return m_name;
		}

		@Override
		public boolean testPick(int x, int y, float scale)
		{
			return false;
		}

		@Override
		public Rect3F getBounds()
		{
			return new Rect3F(1,1,1);
		}
		
		@Override
		public Vector3F getOrigin()
		{
			float destDepth = m_baseAABB.depth;
			return new Vector3F(m_baseAABB.x + m_baseAABB.width / 2,
												m_baseAABB.y + m_baseAABB.height / 2,
												m_baseAABB.depth + destDepth * ((float)m_life / MESSAGE_LIFETIME));
		
		}

		@Override
		public void render(Graphics2D g, int x, int y, float scale)
		{
			m_label.render(g, x, y, scale);
		}
	}
	
	private final class HealthAttributeObserver implements IAttributeChangeObserver
	{
		private final Label m_deltaHpLabel = new Label();
		
		public HealthAttributeObserver()
		{
			m_deltaHpLabel.setStyle(m_statusStyle);
		}
		
		@Override
		public void changed(float delta)
		{
			addStatusMessage(String.format("%.1f", delta));
			
			if(delta < 0)
				bleed();
		}
	}
}
