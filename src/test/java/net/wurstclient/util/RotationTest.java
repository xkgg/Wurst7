/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.*;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Vec3d;

class RotationTest
{
	@Test
	void testGetAngleToSameRotation()
	{
		Rotation r1 = new Rotation(0, 0);
		Rotation r2 = new Rotation(0, 0);
		double angle = r1.getAngleTo(r2);
		assertEquals(0, angle);
		
		r1 = new Rotation(360, 0);
		r2 = new Rotation(0, 0);
		angle = r1.getAngleTo(r2);
		assertEquals(0, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(0, -360);
		angle = r1.getAngleTo(r2);
		assertEquals(0, angle);
	}
	
	@Test
	void testGetAngleTo90DegPitch()
	{
		Rotation r1 = new Rotation(0, 0);
		Rotation r2 = new Rotation(90, 0);
		double angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(-90, 0);
		angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(270, 0);
		angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(-270, 0);
		angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
	}
	
	@Test
	void testGetAngleTo90DegYaw()
	{
		Rotation r1 = new Rotation(0, 0);
		Rotation r2 = new Rotation(0, 90);
		double angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(0, -90);
		angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(0, 270);
		angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
		
		r1 = new Rotation(0, 0);
		r2 = new Rotation(0, -270);
		angle = r1.getAngleTo(r2);
		assertEquals(90, angle);
	}
	
	@Test
	void testGetAngleAcrossZeroYaw()
	{
		Rotation r1 = new Rotation(1, 0);
		Rotation r2 = new Rotation(-1, 0);
		double angle = r1.getAngleTo(r2);
		assertEquals(2, angle, 1e-12);
		
		r1 = new Rotation(1, 0);
		r2 = new Rotation(359, 0);
		angle = r1.getAngleTo(r2);
		assertEquals(2, angle, 1e-12);
		
		r1 = new Rotation(361, 0);
		r2 = new Rotation(-1, 0);
		angle = r1.getAngleTo(r2);
		assertEquals(2, angle, 1e-12);
	}
	
	@Test
	void testGetAngleAcrossZeroPitch()
	{
		Rotation r1 = new Rotation(0, 1);
		Rotation r2 = new Rotation(0, -1);
		double angle = r1.getAngleTo(r2);
		assertEquals(2, angle, 1e-12);
		
		r2 = r2.withPitch(359);
		angle = r1.getAngleTo(r2);
		assertEquals(2, angle, 1e-12);
		
		r1 = r1.withPitch(361);
		angle = r1.getAngleTo(r2);
		assertEquals(2, angle, 1e-12);
	}
	
	@Test
	void testGetAngleToWhenLookingStraightUp()
	{
		Rotation start = new Rotation(0, -90);
		Rotation end = new Rotation(179, -90);
		assertEquals(0, start.getAngleTo(end), 1e-12);
		assertEquals(179, start.getYawPitchDistanceTo(end));
	}
	
	@Test
	void testSphericalAngleDiffersFromYawPitchDistance()
	{
		Rotation start = new Rotation(0, 45);
		Rotation end = new Rotation(90, 45);
		assertEquals(60, start.getAngleTo(end), 1e-12);
		assertEquals(90, start.getYawPitchDistanceTo(end));
	}
	
	@Test
	void testGetAngleAcrossWrappedBoundary()
	{
		Rotation start = new Rotation(179, 0);
		Rotation end = new Rotation(-179, 0);
		assertEquals(2, start.getAngleTo(end), 1e-12);
		assertEquals(2, start.getYawPitchDistanceTo(end));
	}
	
	@Test
	void testSlowlyTurnTowardsPreservesAxisProportions()
	{
		Rotation start = new Rotation(0, 0);
		Rotation end = new Rotation(90, 45);
		assertEquals(new Rotation(10, 5), start.slowlyTurnTowards(end, 10));
		assertEquals(end, start.slowlyTurnTowards(end, 180));
		assertEquals(start, start.slowlyTurnTowards(start, 10));
	}
	
	@Test
	void testSlowlyTurnTowardsAcrossWrappedBoundary()
	{
		Rotation start = new Rotation(179, 0);
		Rotation end = new Rotation(-179, 0);
		assertEquals(new Rotation(180, 0), start.slowlyTurnTowards(end, 1));
	}
	
	@Test
	void testAddAndClampPitch()
	{
		Rotation start = new Rotation(10, 80);
		assertEquals(new Rotation(15, 100), start.add(5, 20));
		assertEquals(new Rotation(15, 90), start.add(5, 20).clampPitch());
		assertEquals(new Rotation(10, -90), start.withPitch(-100).clampPitch());
		assertEquals(start, start.clampPitch());
	}
	
	@Test
	void testLerp()
	{
		Rotation start = new Rotation(10, 20);
		Rotation end = new Rotation(30, 60);
		assertEquals(start, Rotation.lerp(0, start, end));
		assertEquals(end, Rotation.lerp(1, start, end));
		assertEquals(new Rotation(15, 30), Rotation.lerp(0.25F, start, end));
	}
	
	@Test
	void testLookVecPrecisionAtDiagonalRotation()
	{
		Vec3d vec = new Rotation(45, 45).toLookVec();
		assertEquals(-0.5, vec.x, 1e-12);
		assertEquals(-Math.sqrt(0.5), vec.y, 1e-12);
		assertEquals(0.5, vec.z, 1e-12);
		assertEquals(1, vec.length(), 1e-12);
	}
	
	@Test
	void testToLookVec()
	{
		Rotation r = new Rotation(0, 0);
		Vec3d vec = r.toLookVec();
		assertAlmostEquals(0, vec.x);
		assertAlmostEquals(0, vec.y);
		assertAlmostEquals(1, vec.z);
		
		r = new Rotation(90, 0);
		vec = r.toLookVec();
		assertAlmostEquals(-1, vec.x);
		assertAlmostEquals(0, vec.y);
		assertAlmostEquals(0, vec.z);
		
		r = new Rotation(180, 0);
		vec = r.toLookVec();
		assertAlmostEquals(0, vec.x);
		assertAlmostEquals(0, vec.y);
		assertAlmostEquals(-1, vec.z);
		
		r = new Rotation(270, 0);
		vec = r.toLookVec();
		assertAlmostEquals(1, vec.x);
		assertAlmostEquals(0, vec.y);
		assertAlmostEquals(0, vec.z);
	}
	
	@Test
	void testToQuaternion()
	{
		Rotation r = new Rotation(0, 0);
		Quaternionf q = r.toQuaternion();
		assertAlmostEquals(1, q.w);
		assertAlmostEquals(0, q.x);
		assertAlmostEquals(0, q.y);
		assertAlmostEquals(0, q.z);
		
		r = new Rotation(90, 0);
		q = r.toQuaternion();
		assertAlmostEquals(0.70710677F, q.w);
		assertAlmostEquals(0, q.x);
		assertAlmostEquals(-0.70710677F, q.y);
		assertAlmostEquals(0, q.z);
		
		r = new Rotation(180, 0);
		q = r.toQuaternion();
		assertAlmostEquals(0, q.w);
		assertAlmostEquals(0, q.x);
		assertAlmostEquals(1, q.y);
		assertAlmostEquals(0, q.z);
		
		r = new Rotation(270, 0);
		q = r.toQuaternion();
		assertAlmostEquals(0.70710677F, q.w);
		assertAlmostEquals(0, q.x);
		assertAlmostEquals(0.70710677F, q.y);
		assertAlmostEquals(0, q.z);
	}
	
	@Test
	void testWrapped()
	{
		Rotation r = Rotation.wrapped(0, 0);
		assertEquals(0, r.yaw());
		assertEquals(0, r.pitch());
		
		r = Rotation.wrapped(360, 360);
		assertEquals(0, r.yaw());
		assertEquals(0, r.pitch());
		
		r = Rotation.wrapped(270, 270);
		assertEquals(-90, r.yaw());
		assertEquals(-90, r.pitch());
		
		r = Rotation.wrapped(-270, -270);
		assertEquals(90, r.yaw());
		assertEquals(90, r.pitch());
	}
	
	private void assertAlmostEquals(double expected, double actual)
	{
		if(Math.abs(expected - actual) > 1e-6)
			fail("expected: <" + expected + "> but was: <" + actual + ">");
	}
}
