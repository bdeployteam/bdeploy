import {
  AfterViewInit,
  Compiler,
  CompilerFactory,
  COMPILER_OPTIONS,
  Component,
  ComponentRef,
  Inject,
  InjectionToken,
  Input,
  NgModule,
  OnDestroy,
  OnInit,
  Type,
  ViewChild,
  ViewContainerRef,
} from '@angular/core';
import { JitCompilerFactory } from '@angular/platform-browser-dynamic';
import { BehaviorSubject } from 'rxjs';

export const DYNAMIC_BASE_MODULES = new InjectionToken<Type<any>[]>('DYNAMIC_BASE_MODULES');
export function createCompiler(factory: CompilerFactory) {
  return factory.createCompiler();
}

let GLOBAL_COUNTER = 0;

/**
 * This component allows to fully dynamically provide an HTML template as dynamic component
 * using any components available in the CoreModule by using JIT compilation of the template.
 */
@Component({
  selector: 'app-bd-dynamic',
  templateUrl: './bd-dynamic.component.html',
  styleUrls: ['./bd-dynamic.component.css'],
  providers: [
    { provide: COMPILER_OPTIONS, useValue: { useJit: true }, multi: true },
    { provide: CompilerFactory, useClass: JitCompilerFactory, deps: [COMPILER_OPTIONS] },
    { provide: Compiler, useFactory: createCompiler, deps: [CompilerFactory] },
  ],
})
export class BdDynamicComponent implements OnInit, OnDestroy, AfterViewInit {
  /* template */ contents = new BehaviorSubject<string>(null);

  @Input() html: string;
  @ViewChild('vc', { read: ViewContainerRef }) vc: ViewContainerRef;
  private comp: ComponentRef<any>;

  constructor(private compiler: Compiler, @Inject(DYNAMIC_BASE_MODULES) private deps: Type<any>[]) {}

  ngOnInit(): void {}

  ngAfterViewInit(): void {
    if (!this.html) {
      return;
    }

    const tmpComp = Component({ template: this.html, selector: `app-bd-dynamic-${++GLOBAL_COUNTER}` })(class {});
    const tmpMod = NgModule({ declarations: [tmpComp], imports: this.deps })(class {});

    this.compiler.compileModuleAndAllComponentsAsync(tmpMod).then((factories) => {
      const factory = factories.componentFactories[0];
      this.comp = this.vc.createComponent(factory);
    });
  }

  ngOnDestroy(): void {
    if (!!this.comp) {
      this.comp.destroy();
    }
  }
}
