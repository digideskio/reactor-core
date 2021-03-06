/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.MultiReceiver;

/**
 * Given a set of source Publishers the values of that Publisher is forwarded to the
 * actual which responds first with any signal.
 *
 * @param <T> the value type
 *
 * @see <a href="https://github.com/reactor/reactive-streams-commons">Reactive-Streams-Commons</a>
 */
final class MonoFirst<T> extends Mono<T> implements MultiReceiver {

	final Mono<? extends T>[] array;

	final Iterable<? extends Mono<? extends T>> iterable;

	@SafeVarargs
	public MonoFirst(Mono<? extends T>... array) {
		this.array = Objects.requireNonNull(array, "array");
		this.iterable = null;
	}

	public MonoFirst(Iterable<? extends Mono<? extends T>> iterable) {
		this.array = null;
		this.iterable = Objects.requireNonNull(iterable);
	}

	public Mono<T> orAdditionalSource(Mono<? extends T> other) {
		int n = array.length;
		@SuppressWarnings("unchecked")
		Mono<? extends T>[] newArray = new Mono[n + 1];
		System.arraycopy(array, 0, newArray, 0, n);
		newArray[n] = other;

		return new MonoFirst<>(newArray);
	}

	@Override
	public Iterator<?> upstreams() {
		return iterable != null ? iterable.iterator() : Arrays.asList(array)
		                                                      .iterator();
	}

	@Override
	public long upstreamCount() {
		return array != null ? array.length : -1L;
	}

	@SuppressWarnings("unchecked")
	@Override
	//TODO mutualize with FluxFirstEmitting
	public void subscribe(Subscriber<? super T> s) {
		Publisher<? extends T>[] a = array;
		int n;
		if (a == null) {
			n = 0;
			a = new Publisher[8];

			Iterator<? extends Publisher<? extends T>> it;

			try {
				it = iterable.iterator();
			}
			catch (Throwable e) {
				Operators.error(s, Operators.onOperatorError(e));
				return;
			}

			if (it == null) {
				Operators.error(s,
						new NullPointerException("The iterator returned is null"));
				return;
			}

			for (; ; ) {

				boolean b;

				try {
					b = it.hasNext();
				}
				catch (Throwable e) {
					Operators.error(s, Operators.onOperatorError(e));
					return;
				}

				if (!b) {
					break;
				}

				Publisher<? extends T> p;

				try {
					p = it.next();
				}
				catch (Throwable e) {
					Operators.error(s, Operators.onOperatorError(e));
					return;
				}

				if (p == null) {
					Operators.error(s,
							new NullPointerException(
									"The Publisher returned by the iterator is " + "null"));
					return;
				}

				if (n == a.length) {
					Publisher<? extends T>[] c = new Publisher[n + (n >> 2)];
					System.arraycopy(a, 0, c, 0, n);
					a = c;
				}
				a[n++] = p;
			}

		}
		else {
			n = a.length;
		}

		if (n == 0) {
			Operators.complete(s);
			return;
		}
		if (n == 1) {
			Publisher<? extends T> p = a[0];

			if (p == null) {
				Operators.error(s,
						new NullPointerException("The single source Publisher is null"));
			}
			else {
				p.subscribe(s);
			}
			return;
		}

		FluxFirstEmitting.RaceCoordinator<T> coordinator =
				new FluxFirstEmitting.RaceCoordinator<>(n);

		coordinator.subscribe(a, n, s);
	}

	//TODO the ambAdditionalSource optimization from FluxFirstEmitting could also be applied

}